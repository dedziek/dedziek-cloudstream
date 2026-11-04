export function repositoryManifest(repository) {
  return {
    name: 'Dedziek CloudStream — polskie źródła',
    description: 'Filman, AnimeWatch, test logowania AnimeGate oraz ObejrzyjTo autorstwa Phisher98.',
    manifestVersion: 1,
    pluginLists: ['plugins.json', 'phisher-plugins.json'].map(
      name => `https://raw.githubusercontent.com/${repository}/builds/${name}`),
  };
}
