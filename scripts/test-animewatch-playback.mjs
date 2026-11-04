import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

// Execute the actual script shipped in the Android plugin, without maintaining a second copy.
const kotlin = readFileSync(new URL('../AnimeWatchProvider/src/main/kotlin/pl/animewatchcloudstream/AnimeWatchPlayback.kt', import.meta.url), 'utf8');
const playerScript = kotlin.match(/val playerScript = """([\s\S]*?)"""\.trimIndent\(\)/)[1].trim();
const script = new vm.Script(playerScript);
const nonce = '01234567-89ab-cdef-0123-456789abcdef';
const frameScript = new vm.Script(kotlin.match(/val frameScriptTemplate = """([\s\S]*?)"""\.trimIndent\(\)/)[1]
  .replace('__AW_READ_SOURCE__', playerScript.replace(/;$/, ''))
  .replaceAll('__AW_NONCE__', nonce));
const page = 'https://player.example/embed/code';
const hls = 'https://cdn.example/master.m3u8?token=a%2Fb&expires=123';

function context(player, video = null) {
  return vm.createContext({
    URL,
    location: { href: page },
    window: player ? { jwplayer: () => player } : {},
    document: {
      querySelector(selector) {
        assert.equal(selector, '.jwplayer:not(.jw-flag-ads) video.jw-video');
        return video;
      },
    },
  });
}

function resolve(ctx) {
  const value = script.runInContext(ctx);
  return value === null ? null : JSON.parse(value);
}

test('finds the ready content playlist without pressing Play', () => {
  const player = {
    getPlaylistItem: () => ({ sources: [{ file: hls, type: 'hls' }] }),
    play: () => assert.fail('playlist extraction must not require playback'),
  };
  assert.deepEqual(resolve(context(player)), { url: hls, type: 'hls', referer: page });
});

test('waits for delayed player initialisation and succeeds on the next poll', () => {
  const ctx = context();
  assert.equal(resolve(ctx), null);
  ctx.window.jwplayer = () => { throw new Error('player not ready'); };
  assert.equal(resolve(ctx), null);
  ctx.window.jwplayer = () => ({ getPlaylistItem: () => ({ file: hls }) });
  assert.equal(resolve(ctx).url, hls);
});

test('supports getPlaylist and the url property used by source metadata', () => {
  assert.equal(resolve(context({ getPlaylist: () => [{ sources: [{ url: hls }] }] })).url, hls);
});

test('recognises opaque HLS source URLs by their declared type', () => {
  const url = 'https://cdn.example/playback?token=abc';
  assert.deepEqual(resolve(context({ getPlaylistItem: () => ({ sources: [{ file: url, type: 'application/vnd.apple.mpegurl' }] }) })),
    { url, type: 'application/vnd.apple.mpegurl', referer: page });
});

test('resolves relative MP4 sources against the player document', () => {
  assert.equal(resolve(context({ getPlaylistItem: () => ({ file: '/video.mp4?token=abc', type: 'mp4' }) })).url,
    'https://player.example/video.mp4?token=abc');
});

test('ignores blob sources and reads the primary video source as a fallback', () => {
  assert.equal(resolve(context({ getPlaylistItem: () => ({ file: 'blob:https://player.example/id' }) }, { currentSrc: hls })).url, hls);
});

test('does not return HTTP, script, HTML or unresolved blob sources', () => {
  for (const file of ['http://cdn.example/video.mp4', 'javascript:alert(1)', 'https://ad.example/popup', 'blob:https://player.example/id']) {
    assert.equal(resolve(context({ getPlaylistItem: () => ({ file }) })), null);
  }
});

test('skips unsupported sources and returns the first content media source', () => {
  assert.equal(resolve(context({ getPlaylistItem: () => ({ sources: [{ file: 'https://ad.example/pixel.js' }, { file: hls }] }) })).url, hls);
});

function frameContext(player) {
  const ctx = context(player);
  const timers = [];
  const listeners = new Map();
  const messages = [];
  const select = ctx.document.querySelector;
  ctx.document.querySelector = selector => selector === '.captcha-gate' ? ctx.gate || null : select(selector);
  ctx.setTimeout = fn => { timers.push(fn); };
  ctx.window.addEventListener = (type, fn) => listeners.set(type, fn);
  ctx.window.AnimeWatchBridge = { postSource: (token, payload) => messages.push({ token, source: JSON.parse(payload) }) };
  ctx.fetch = () => assert.fail('the source monitor must not replace hosting requests');
  return { ctx, timers, listeners, messages, tick: () => timers.shift()?.() };
}

test('observes the child frame after its real parent initialises the player', () => {
  // Synthetic two-frame regression: the dedicated frame stays empty until parent
  // context arrives. The v2 top-document probe cannot observe that child's player.
  const outer = frameContext();
  const inner = frameContext();
  inner.ctx.location.href = 'https://frame.example/ooa4/code';
  frameScript.runInContext(outer.ctx);
  frameScript.runInContext(inner.ctx);
  outer.tick(); inner.tick();
  assert.equal(outer.messages.length, 0);
  assert.equal(inner.messages.length, 0);
  // Stand-in for the hosting's own parent/iframe handshake; the monitor never
  // fabricates a context message or changes the embedding relationship.
  inner.ctx.window.jwplayer = () => ({ getPlaylistItem: () => ({ file: hls, type: 'hls' }) });
  outer.tick(); inner.tick();
  assert.equal(outer.messages.length, 0);
  assert.deepEqual(inner.messages, [{ token: nonce, source: { url: hls, type: 'hls', referer: inner.ctx.location.href } }]);
});

test('sends one source to the native bridge and stops polling', () => {
  const frame = frameContext({ getPlaylistItem: () => ({ file: hls }) });
  frameScript.runInContext(frame.ctx);
  frame.tick();
  assert.equal(frame.messages.length, 1);
  assert.equal(frame.timers.length, 0);
});

test('stops the observer when its player document is unloaded', () => {
  const frame = frameContext();
  frameScript.runInContext(frame.ctx);
  frame.tick();
  frame.listeners.get('pagehide')();
  frame.ctx.window.jwplayer = () => ({ getPlaylistItem: () => ({ file: hls }) });
  frame.tick();
  assert.equal(frame.messages.length, 0);
  assert.equal(frame.timers.length, 0);
});

test('bounds polling when the hosting never provides a source', () => {
  const frame = frameContext();
  frameScript.runInContext(frame.ctx);
  for (let i = 0; i < 500 && frame.timers.length; i++) frame.tick();
  assert.equal(frame.timers.length, 0);
  assert.equal(frame.messages.length, 0);
});

test('reports the verification gate once without clicking or solving it', () => {
  const frame = frameContext();
  frame.ctx.gate = { click: () => assert.fail('verification must be completed by the user') };
  frameScript.runInContext(frame.ctx);
  frame.tick(); frame.tick();
  assert.deepEqual(frame.messages, [{ token: nonce, source: { error: 'captcha_required', referer: page } }]);
  // In the explicitly opened verification window, the site's normal user flow
  // can provide a player later. The same observer then captures its ready source.
  frame.ctx.gate = null;
  frame.ctx.window.jwplayer = () => ({ getPlaylistItem: () => ({ file: hls, type: 'hls' }) });
  frame.tick();
  assert.equal(frame.messages.length, 2);
  assert.equal(frame.messages[1].source.url, hls);
  assert.equal(frame.timers.length, 0);
});
