export function repositoryManifest(repository) {
  return {
    name: 'Dedziek CloudStream — polskie źródła',
    description: 'Polskie filmy, seriale i anime: Filman, AnimeWatch, AnimeGate, Ekino TV oraz ObejrzyjTo od Phisher98.',
    manifestVersion: 1,
    pluginLists: ['plugins.json', 'phisher-plugins.json'].map(
      name => `https://raw.githubusercontent.com/${repository}/builds/${name}`),
  };
}
