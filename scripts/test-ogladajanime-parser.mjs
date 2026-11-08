import assert from 'node:assert/strict';
import { test } from 'node:test';

test('OgladajAnime card regex and structure validation', () => {
  const sampleCard = `
    <div class="col-12 col-lg-6 anime-item m-0 mb-3 mb-md-0 p-0 p-md-3">
      <div class="card bg-white ">
        <figure class="col-4 m-0 p-0">
          <a href="/anime/naruto-shippuuden" aria-label="Naruto Shippuuden">
            <img src="https://cdn.ogladajanime.pl/images/anime_new/664/0.webp?20250121233823" alt="Naruto Shippuuden" />
          </a>
          <span class="badge badge-warning">TV</span>
        </figure>
      </div>
    </div>
  `;

  const linkMatch = sampleCard.match(/href=["'](\/anime\/[^"']+)["']/);
  assert.ok(linkMatch);
  assert.equal(linkMatch[1], '/anime/naruto-shippuuden');

  const titleMatch = sampleCard.match(/aria-label=["']([^"']+)["']/);
  assert.ok(titleMatch);
  assert.equal(titleMatch[1], 'Naruto Shippuuden');

  const imgMatch = sampleCard.match(/src=["'](https:\/\/cdn\.ogladajanime\.pl\/images\/anime_new\/[^"']+)["']/);
  assert.ok(imgMatch);
  assert.equal(imgMatch[1], 'https://cdn.ogladajanime.pl/images/anime_new/664/0.webp?20250121233823');
});

test('OgladajAnime episode parsing and player extraction regex', () => {
  const sampleEpisode = `
    <a class="list-group-item" title="Powrót do wioski" onclick="changeEpisode(this, '1', 62788); return false;" value="1" ep_id="62788">
      <span class="badge">1</span>
      <p class="text-trim">Powrót do wioski</p>
    </a>
  `;

  const epIdMatch = sampleEpisode.match(/ep_id=["'](\d+)["']/);
  assert.ok(epIdMatch);
  assert.equal(epIdMatch[1], '62788');

  const valueMatch = sampleEpisode.match(/value=["']([^"']+)["']/);
  assert.ok(valueMatch);
  assert.equal(valueMatch[1], '1');

  const titleMatch = sampleEpisode.match(/title=["']([^"']+)["']/);
  assert.ok(titleMatch);
  assert.equal(titleMatch[1], 'Powrót do wioski');
});
