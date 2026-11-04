import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

// Execute the actual script shipped in the Android plugin, without maintaining a second copy.
const kotlin = readFileSync(new URL('../AnimeWatchProvider/src/main/kotlin/pl/animewatchcloudstream/AnimeWatchPlayback.kt', import.meta.url), 'utf8');
const script = new vm.Script(kotlin.match(/val playerScript = """([\s\S]*?)"""\.trimIndent\(\)/)[1]);
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
