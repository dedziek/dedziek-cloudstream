export const ownPlugins = new Map([
  ['FilmanProvider', 'pl.filmancloudstream.FilmanPlugin'],
  ['AnimeWatchProvider', 'pl.animewatchcloudstream.AnimeWatchPlugin'],
  ['AnimeGateProvider', 'pl.animegatecloudstream.AnimeGatePlugin'],
  ['EkinoTvProvider', 'pl.dedziek.ekinotv.EkinoTvPlugin'],
]);

export const retiredPlugins = ['EkinoProvider'];

export function validateOwnCatalogue(entries) {
  if (!Array.isArray(entries) || entries.length !== ownPlugins.size ||
      new Set(entries.map(entry => entry.internalName)).size !== ownPlugins.size ||
      entries.some(entry => !ownPlugins.has(entry.internalName))) {
    throw new Error('Lista własnych dodatków nie pasuje do konfiguracji publikacji.');
  }
}
