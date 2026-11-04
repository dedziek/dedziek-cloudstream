import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { repositoryManifest } from './repository-config.mjs';

test('source manifest and publication advertise the same two plugin lists', async () => {
  const manifest = JSON.parse(await readFile(new URL('../repo.json', import.meta.url), 'utf8'));
  assert.deepEqual(manifest, repositoryManifest('dedziek/dedziek-cloudstream'));
  assert.equal(manifest.pluginLists.length, 2);
});

test('fork publications use their own catalogue URLs', () => {
  assert.deepEqual(repositoryManifest('example/fork').pluginLists, [
    'https://raw.githubusercontent.com/example/fork/builds/plugins.json',
    'https://raw.githubusercontent.com/example/fork/builds/phisher-plugins.json',
  ]);
});
