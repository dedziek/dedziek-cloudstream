#!/usr/bin/env python3
"""Publish only Phisher's ObejrzyjTo catalogue entry; never execute or rehost its package."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import zipfile

UPSTREAM = "phisher98/cloudstream-extensions-phisher"
RAW = f"https://raw.githubusercontent.com/{UPSTREAM}/"
BRANCH_API = f"https://api.github.com/repos/{UPSTREAM}/branches/builds"
INTERNAL_NAME = "ObejrzyjTo"
PACKAGE = f"{INTERNAL_NAME}.cs3"
MAX_PACKAGE_SIZE = 32 * 1024 * 1024


class Client:
    def get_bytes(self, url, limit):
        headers = {"User-Agent": "Dedziek-CloudStream-catalog-sync", "Accept": "application/vnd.github+json"}
        token = os.environ.get("GITHUB_TOKEN")
        if url == BRANCH_API and token:
            headers["Authorization"] = f"Bearer {token}"
        with urlopen(Request(url, headers=headers), timeout=30) as response:
            body = response.read(limit + 1)
        if len(body) > limit:
            raise ValueError("Odpowiedź źródła przekracza limit rozmiaru.")
        return body

    def get_json(self, url):
        return json.loads(self.get_bytes(url, 2 * 1024 * 1024))


def validate_entry(entry):
    if not isinstance(entry, dict) or entry.get("internalName") != INTERNAL_NAME:
        raise ValueError("Nieprawidłowy identyfikator dodatku Phishera.")
    allowed_urls = {f"{RAW}builds/{PACKAGE}", f"{RAW}refs/heads/builds/{PACKAGE}"}
    if entry.get("url") not in allowed_urls or entry.get("repositoryUrl") != f"https://github.com/{UPSTREAM}":
        raise ValueError("Adres dodatku nie wskazuje oryginalnego repozytorium Phishera.")
    if entry.get("apiVersion") != 1 or entry.get("language") != "pl":
        raise ValueError("Nieobsługiwane API lub język dodatku.")
    if type(entry.get("version")) is not int or entry["version"] < -1:
        raise ValueError("Nieprawidłowy numer wersji.")
    if type(entry.get("fileSize")) is not int or not 0 < entry["fileSize"] <= MAX_PACKAGE_SIZE:
        raise ValueError("Nieprawidłowy rozmiar paczki.")
    if not isinstance(entry.get("fileHash"), str) or not re.fullmatch(r"sha256-[a-f0-9]{64}", entry["fileHash"]):
        raise ValueError("Brak prawidłowej sumy SHA-256.")
    authors = entry.get("authors")
    if not isinstance(authors, list) or "Phisher98" not in authors:
        raise ValueError("Brak oryginalnego autora w metadanych.")
    return dict(entry)


def validate_package(data, entry):
    if len(data) != entry["fileSize"] or "sha256-" + hashlib.sha256(data).hexdigest() != entry["fileHash"]:
        raise ValueError("Rozmiar lub SHA-256 paczki nie zgadza się z metadanymi.")
    with zipfile.ZipFile(io.BytesIO(data)) as package:
        names = package.namelist()
        if names.count("manifest.json") != 1 or names.count("classes.dex") != 1:
            raise ValueError("Paczka musi mieć jeden manifest i jeden classes.dex.")
        if package.getinfo("manifest.json").file_size > 65_536:
            raise ValueError("Manifest paczki jest zbyt duży.")
        manifest = json.loads(package.read("manifest.json"))
        if manifest.get("name") != INTERNAL_NAME or manifest.get("version") != entry["version"]:
            raise ValueError("Manifest paczki nie odpowiada wybranemu dodatkowi i wersji.")
        if not isinstance(manifest.get("pluginClassName"), str) or not manifest["pluginClassName"]:
            raise ValueError("Manifest nie zawiera klasy dodatku.")
        with package.open("classes.dex") as dex:
            if not re.fullmatch(b"dex\n[0-9]{3}\x00", dex.read(8)):
                raise ValueError("Nieprawidłowy nagłówek classes.dex.")


def synchronize(output, previous=None, client=None):
    client = client or Client()
    output = Path(output)
    commit = client.get_json(BRANCH_API)["commit"]["sha"]
    if not isinstance(commit, str) or not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Nieprawidłowy commit źródłowy.")
    plugins = client.get_json(f"{RAW}{commit}/plugins.json")
    if not isinstance(plugins, list):
        raise ValueError("Lista dodatków Phishera nie jest tablicą.")
    matches = [p for p in plugins if isinstance(p, dict) and p.get("internalName") == INTERNAL_NAME]
    if len(matches) != 1:
        raise ValueError("Nie znaleziono dokładnie jednego ObejrzyjTo; zachowano poprzednią listę.")
    entry = validate_entry(matches[0])
    current_url = f"{RAW}{commit}/{PACKAGE}"
    entry["url"] = current_url

    # A stable pin avoids catalogue changes for unrelated Phisher updates, while
    # immutable package URLs keep the manifest, version and checksum in agreement.
    previous = Path(previous) if previous else output
    if previous.is_file():
        old = json.loads(previous.read_text(encoding="utf-8"))
        if isinstance(old, list) and len(old) == 1 and isinstance(old[0], dict):
            old = old[0]
            pin = old.get("url", "")
            if (old.get("internalName") == INTERNAL_NAME and isinstance(pin, str)
                    and re.fullmatch(re.escape(RAW) + r"[a-f0-9]{40}/" + re.escape(PACKAGE), pin)
                    and all(old.get(key) == entry[key] for key in ("fileHash", "fileSize", "version"))):
                entry["url"] = pin

    try:
        data = client.get_bytes(entry["url"], MAX_PACKAGE_SIZE)
    except HTTPError as error:
        if error.code != 404 or entry["url"] == current_url:
            raise
        # The author may prune old builds commits. Repin identical bytes to the
        # current commit, without changing the plugin identity or author version.
        entry["url"] = current_url
        data = client.get_bytes(current_url, MAX_PACKAGE_SIZE)
    validate_package(data, entry)
    text = json.dumps([entry], ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if output.is_file() and output.read_text(encoding="utf-8") == text:
        print(f"{INTERNAL_NAME} v{entry['version']}: lista jest aktualna.")
        return False
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent, delete=False) as handle:
            temporary = Path(handle.name)
            handle.write(text)
        temporary.replace(output)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    print(f"{INTERNAL_NAME} v{entry['version']}: zapisano zweryfikowaną listę {output}.")
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--previous", type=Path)
    args = parser.parse_args()
    synchronize(args.output, args.previous)
