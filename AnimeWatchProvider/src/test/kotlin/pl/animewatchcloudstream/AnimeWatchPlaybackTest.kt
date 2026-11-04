package pl.animewatchcloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeWatchPlaybackTest {
    @Test fun recognisesSignedMediaWithoutTreatingTheQueryAsTheExtension() {
        assertTrue(AnimeWatchPlayback.isMedia("https://cdn.example/master.m3u8?token=a%2Fb&expires=123"))
        assertTrue(AnimeWatchPlayback.isHls("https://cdn.example/MASTER.M3U8?token=123"))
        assertTrue(AnimeWatchPlayback.isMedia("https://cdn.example/video.mp4?download=1"))
        assertFalse(AnimeWatchPlayback.isHls("https://cdn.example/video.mp4?name=master.m3u8"))
    }

    @Test fun recognisesHlsWithOpaqueUrlsUsingThePlayersSourceType() {
        for (type in listOf("hls", "m3u8", "application/x-mpegURL", "application/vnd.apple.mpegurl")) {
            assertTrue(type, AnimeWatchPlayback.isHls("https://cdn.example/stream?token=123", type))
        }
        assertFalse(AnimeWatchPlayback.isHls("https://cdn.example/stream", "video/mp4"))
    }

    @Test fun rejectsPlayerPagesSegmentsAndAdvertisementScriptsAsMediaRequests() {
        for (url in listOf("https://host.example/e/code", "https://cdn.example/segment.ts", "https://ad.example/pixel.js?file=a.mp4")) {
            assertFalse(url, AnimeWatchPlayback.isMedia(url))
        }
    }

    @Test fun rejectsLocalFilesCredentialsAndNonPublicHosts() {
        for (url in listOf("http://cdn.example/video.mp4", "file:///video.mp4", "javascript:alert(1)",
            "https://127.0.0.1/video.mp4", "https://[::1]/video.mp4", "https://localhost/video.mp4",
            "https://server.internal/video.mp4", "https://server.local/video.mp4", "https://server.lan/video.mp4",
            "https://user:pass@cdn.example/video.mp4", "https://cdn.example:444/video.mp4")) {
            assertNull(url, AnimeWatchPlayback.publicHttps(url))
        }
    }

    @Test fun acceptsOnlyThePlayersOriginAsTheSourceOfCapturedMedia() {
        val player = AnimeWatchPlayback.publicHttps("https://player.example/e/code")!!
        assertTrue(AnimeWatchPlayback.sameOrigin(player, AnimeWatchPlayback.publicHttps("https://PLAYER.example:443/")!!))
        assertFalse(AnimeWatchPlayback.sameOrigin(player, AnimeWatchPlayback.publicHttps("https://ad.example/")!!))
        assertFalse(AnimeWatchPlayback.sameOrigin(player, AnimeWatchPlayback.publicHttps("https://player.example.ad.example/")!!))
    }
}
