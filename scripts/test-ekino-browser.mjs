import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';

const source = await readFile(new URL('../EkinoTvProvider/src/main/kotlin/pl/dedziek/ekinotv/WebSession.kt', import.meta.url), 'utf8');
const script = source.match(/internal val SNAPSHOT = """([\s\S]*?)"""/)[1];
const document = { readyState: 'interactive', documentElement: { outerHTML: '<h1 class="title">Film</h1>' } };

function snapshot({ id = 100, frames = [], timing = undefined } = {}) {
  const window = { frames, ShowPlayer() {} };
  const performance = { timeOrigin: id, timing };
  const value = JSON.parse(runInNewContext(script, {
    window, document, location: { href: 'https://ekino.ws/movie/show/test/42' }, performance, URL,
  }));
  return { value, window, performance };
}

test('the actual browser script reports a parsed document while other resources are loading', () => {
  const { value } = snapshot();
  assert.equal(value.readyState, 'interactive');
  assert.equal(value.documentId, 100);
  assert.equal(value.hasShowPlayer, true);
  assert.equal(value.html, document.documentElement.outerHTML);
});

test('document identity stays stable across probes and changes on the next navigation', () => {
  assert.equal(snapshot().value.documentId, snapshot().value.documentId);
  assert.notEqual(snapshot().value.documentId, snapshot({ id: 200 }).value.documentId);
  assert.equal(snapshot({ id: 0, timing: { navigationStart: 300 } }).value.documentId, 300);
});

test('inaccessible or still-loading frames cannot hide a ready same-origin wrapper', () => {
  const inaccessible = { get location() { throw new Error('cross origin'); } };
  const loading = { location: { href: 'https://ekino.ws/watch/f/player/loading' }, document: { readyState: 'loading' }, frames: [] };
  const ready = { location: { href: 'https://ekino.ws/watch/f/player/ready' }, document, frames: [] };
  const { value } = snapshot({ frames: [inaccessible, loading, ready] });
  assert.deepEqual(value.frames.map(frame => frame.url), ['https://ekino.ws/watch/f/player/ready']);
});
