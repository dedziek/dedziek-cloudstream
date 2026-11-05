import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ownPlugins, validateOwnCatalogue } from './own-plugins.mjs';

test('new Ekino identity publishes alongside the other three providers', () => {
  const entries = ['FilmanProvider', 'AnimeWatchProvider', 'AnimeGateProvider', 'EkinoTvProvider'].map(internalName => ({ internalName }));
  assert.doesNotThrow(() => validateOwnCatalogue(entries));
  assert.equal(ownPlugins.get('EkinoTvProvider'), 'pl.dedziek.ekinotv.EkinoTvPlugin');
});

test('old, mixed and incomplete Ekino catalogues cannot be published', () => {
  const entries = ['FilmanProvider', 'AnimeWatchProvider', 'AnimeGateProvider', 'EkinoTvProvider'].map(internalName => ({ internalName }));
  assert.throws(() => validateOwnCatalogue(entries.map(entry => ({ internalName: entry.internalName.replace('EkinoTvProvider', 'EkinoProvider') }))));
  assert.throws(() => validateOwnCatalogue([...entries, { internalName: 'EkinoProvider' }]));
  assert.throws(() => validateOwnCatalogue(entries.slice(0, 3)));
  assert.throws(() => validateOwnCatalogue([...entries.slice(0, 3), entries[0]]));
});
