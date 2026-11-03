import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const repository = process.argv[2] ?? process.env.GITHUB_REPOSITORY;
if (!repository || !/^[A-Za-z0-9-]+\/[A-Za-z0-9._-]+$/.test(repository)) {
  throw new Error('Podaj repozytorium owner/repo, np. dedziek/dedziek-cloudstream.');
}
const rawBase = `https://raw.githubusercontent.com/${repository}/builds/`;
const pluginName = 'FilmanProvider';
const compiled = path.join(root, pluginName, 'build', `${pluginName}.cs3`);
const manifest = JSON.parse(await readFile(path.join(root, pluginName, 'build', 'intermediates', 'manifest.json'), 'utf8'));
const entries = JSON.parse(await readFile(path.join(root, 'build', 'plugins.json'), 'utf8'));
if (!Array.isArray(entries) || entries.length !== 1) throw new Error('Oczekiwano jednego dodatku.');
const entry = entries[0];
const bytes = await readFile(compiled);
const hash = `sha256-${createHash('sha256').update(bytes).digest('hex')}`;
if (entry.internalName !== pluginName || entry.name !== pluginName || manifest.name !== pluginName ||
    manifest.pluginClassName !== 'pl.filmancloudstream.FilmanPlugin' ||
    !Number.isSafeInteger(entry.version) || entry.version < 1 || manifest.version !== entry.version ||
    entry.apiVersion !== 1 || entry.fileSize !== bytes.length || entry.fileHash !== hash) {
  throw new Error('Metadane nie pasują do skompilowanego dodatku.');
}
if (entry.url !== `${rawBase}${pluginName}.cs3` || entry.repositoryUrl !== `https://github.com/${repository}`) {
  throw new Error('Adresy w plugins.json nie pasują do repozytorium. Ustaw GITHUB_REPOSITORY przed kompilacją.');
}
const repo = {
  name: 'Filman CloudStream — wersja testowa',
  description: 'Dodatek Filman dla darmowego konta. Logowanie na urządzeniu użytkownika.',
  manifestVersion: 1,
  pluginLists: [`${rawBase}plugins.json`],
};
const out = path.join(root, 'build', 'publication');
await mkdir(out, { recursive: true });
await copyFile(compiled, path.join(out, `${pluginName}.cs3`));
await writeFile(path.join(out, 'plugins.json'), `${JSON.stringify(entries, null, 2)}\n`, 'utf8');
await writeFile(path.join(out, 'repo.json'), `${JSON.stringify(repo, null, 2)}\n`, 'utf8');
await writeFile(path.join(out, 'SHA256SUMS.txt'), `${hash.slice(7)}  ${pluginName}.cs3\n`, 'utf8');
console.log(`Przygotowano ${out}`);
console.log(`Adres repozytorium po publikacji: ${rawBase}repo.json`);
