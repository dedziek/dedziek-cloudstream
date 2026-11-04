import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from urllib.error import HTTPError
import zipfile

spec = importlib.util.spec_from_file_location("phisher_sync", Path(__file__).with_name("sync-phisher.py"))
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
A = "a" * 40
B = "b" * 40


def fixture(version=3, manifest_version=None, manifest_name="ObejrzyjTo"):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps({
            "name": manifest_name, "version": manifest_version if manifest_version is not None else version,
            "pluginClassName": "com.phisher.ObejrzyjToPlugin",
        }))
        archive.writestr("classes.dex", b"dex\n035\x00" + bytes([version]))
    data = out.getvalue()
    entry = {
        "name": "ObejrzyjTo", "internalName": "ObejrzyjTo", "version": version, "status": 1,
        "apiVersion": 1, "language": "pl", "authors": ["Phisher98"], "tvTypes": ["Movie", "TvSeries"],
        "repositoryUrl": f"https://github.com/{sync.UPSTREAM}", "url": f"{sync.RAW}builds/{sync.PACKAGE}",
        "fileSize": len(data), "fileHash": "sha256-" + hashlib.sha256(data).hexdigest(),
    }
    return entry, data


class FakeClient:
    def __init__(self, entry, data, commit=A):
        self.commit = commit
        self.plugins = [{"internalName": "UnrelatedPlugin"}, copy.deepcopy(entry)]
        self.packages = {f"{sync.RAW}{commit}/{sync.PACKAGE}": data}
        self.downloads = []

    def get_json(self, url):
        if url == sync.BRANCH_API:
            return {"commit": {"sha": self.commit}}
        assert url == f"{sync.RAW}{self.commit}/plugins.json", url
        return self.plugins

    def get_bytes(self, url, limit):
        self.downloads.append(url)
        value = self.packages[url]
        if isinstance(value, Exception):
            raise value
        assert len(value) <= limit
        return value


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name) / "phisher-plugins.json"
        self.entry, self.data = fixture()

    def test_publishes_only_selected_plugin_with_attribution_and_immutable_url(self):
        client = FakeClient(self.entry, self.data)
        self.assertTrue(sync.synchronize(self.output, client=client))
        published = json.loads(self.output.read_text())
        self.assertEqual(len(published), 1)
        self.assertEqual(published[0], {**self.entry, "url": f"{sync.RAW}{A}/{sync.PACKAGE}"})

    def test_author_version_update_is_selected_automatically(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        entry, data = fixture(version=4)
        self.assertTrue(sync.synchronize(self.output, client=FakeClient(entry, data, B)))
        published = json.loads(self.output.read_text())[0]
        self.assertEqual(published["version"], 4)
        self.assertEqual(published["url"], f"{sync.RAW}{B}/{sync.PACKAGE}")
        self.assertEqual(published["fileHash"], entry["fileHash"])

    def test_unrelated_upstream_changes_keep_existing_pin_and_do_not_rewrite(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        before = self.output.read_bytes()
        client = FakeClient(self.entry, self.data, B)
        client.packages[f"{sync.RAW}{A}/{sync.PACKAGE}"] = self.data
        self.assertFalse(sync.synchronize(self.output, client=client))
        self.assertEqual(self.output.read_bytes(), before)
        self.assertEqual(client.downloads, [f"{sync.RAW}{A}/{sync.PACKAGE}"])

    def test_reads_previous_catalog_from_a_separate_publication_directory(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        other = self.output.parent / "publication" / self.output.name
        client = FakeClient(self.entry, self.data, B)
        client.packages[f"{sync.RAW}{A}/{sync.PACKAGE}"] = self.data
        sync.synchronize(other, previous=self.output, client=client)
        self.assertEqual(other.read_bytes(), self.output.read_bytes())

    def test_pruned_pin_can_be_replaced_by_identical_current_package(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        client = FakeClient(self.entry, self.data, B)
        old_url = f"{sync.RAW}{A}/{sync.PACKAGE}"
        client.packages[old_url] = HTTPError(old_url, 404, "Not found", {}, None)
        sync.synchronize(self.output, client=client)
        self.assertEqual(json.loads(self.output.read_text())[0]["url"], f"{sync.RAW}{B}/{sync.PACKAGE}")

    def test_missing_duplicate_or_foreign_plugin_never_replaces_good_catalog(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        before = self.output.read_bytes()
        invalid = [[], [self.entry, self.entry]]
        for field, value in [("url", "https://foreign.example/plugin.cs3"),
                             ("repositoryUrl", "https://github.com/other/repo"),
                             ("authors", ["Someone else"]), ("fileHash", ""), ("version", True)]:
            invalid.append([{**self.entry, field: value}])
        for plugins in invalid:
            with self.subTest(plugins=plugins):
                client = FakeClient(self.entry, self.data, B)
                client.plugins = plugins
                with self.assertRaises(ValueError):
                    sync.synchronize(self.output, client=client)
                self.assertEqual(self.output.read_bytes(), before)

    def test_wrong_checksum_or_manifest_never_replaces_good_catalog(self):
        sync.synchronize(self.output, client=FakeClient(self.entry, self.data))
        before = self.output.read_bytes()
        cases = [(self.entry, self.data + b"corrupt"), fixture(4, manifest_version=3),
                 fixture(4, manifest_name="UnrelatedPlugin")]
        for entry, data in cases:
            client = FakeClient(entry, data, B)
            client.packages[f"{sync.RAW}{A}/{sync.PACKAGE}"] = data
            with self.assertRaises(ValueError):
                sync.synchronize(self.output, client=client)
            self.assertEqual(self.output.read_bytes(), before)

    def test_upstream_disabled_status_is_preserved(self):
        client = FakeClient({**self.entry, "status": 0}, self.data)
        sync.synchronize(self.output, client=client)
        self.assertEqual(json.loads(self.output.read_text())[0]["status"], 0)


if __name__ == "__main__":
    unittest.main()
