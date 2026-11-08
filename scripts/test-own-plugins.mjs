import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ownPlugins, validateOwnCatalogue } from './own-plugins.mjs';

test('published providers include all five active providers', () => {
  const entries = ['FilmanProvider', 'AnimeWatchProvider', 'AnimeGateProvider', 'EkinoTvProvider', 'OgladajAnimeProvider'].map(internalName => ({ internalName }));
  assert.doesNotThrow(() => validateOwnCatalogue(entries));
  assert.equal(ownPlugins.get('EkinoTvProvider'), 'pl.dedziek.ekinotv.EkinoTvPlugin');
  assert.equal(ownPlugins.get('OgladajAnimeProvider'), 'pl.ogladajanimecloudstream.OgladajAnimePlugin');
});

test('old, mixed and incomplete catalogues cannot be published', () => {
  const entries = ['FilmanProvider', 'AnimeWatchProvider', 'AnimeGateProvider', 'EkinoTvProvider', 'OgladajAnimeProvider'].map(internalName => ({ internalName }));
  assert.throws(() => validateOwnCatalogue(entries.map(entry => ({ internalName: entry.internalName.replace('EkinoTvProvider', 'EkinoProvider') }))));
  assert.throws(() => validateOwnCatalogue([...entries, { internalName: 'EkinoProvider' }]));
  assert.throws(() => validateOwnCatalogue(entries.slice(0, 4)));
  assert.throws(() => validateOwnCatalogue([...entries.slice(0, 4), entries[0]]));
});
