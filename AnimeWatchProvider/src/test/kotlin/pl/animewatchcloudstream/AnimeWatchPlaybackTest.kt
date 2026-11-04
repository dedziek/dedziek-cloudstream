package pl.animewatchcloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeWatchPlaybackTest {
    private val parent = "https://host.example/e/code"
    private val frame = "https://frame.example/ooa4/code"
    private val pages = listOf(parent, frame).map { AnimeWatchPlayback.publicHttps(it)!! }
    private val nonce = "01234567-89ab-cdef-0123-456789abcdef"

    @Test fun recognisesBothParentAndCrossOriginPlayerWithoutTrustingAdvertisementFrames() {
        assertTrue(AnimeWatchPlayback.isPlayerPage(parent, pages))
        assertTrue(AnimeWatchPlayback.isPlayerPage("$frame?lang=pl", pages))
        assertFalse(AnimeWatchPlayback.isPlayerPage("https://advert.example/ooa4/code", pages))
        assertFalse(AnimeWatchPlayback.isPlayerPage("https://frame.example/ooa4/different", pages))
    }

    @Test fun augmentsOnlyTheTrustedPlayersLibraryRequest() {
        val script = "https://frame.example/assets/index-DocunfmE.js?v=123"
        assertTrue(AnimeWatchPlayback.isPlayerScript(script, frame, pages))
        assertTrue(AnimeWatchPlayback.isPlayerScript(script, "https://frame.example/", pages))
        assertFalse(AnimeWatchPlayback.isPlayerScript(script, "https://advert.example/", pages))
        assertFalse(AnimeWatchPlayback.isPlayerScript(script, "https://frame.example/advertisement", pages))
        assertFalse(AnimeWatchPlayback.isPlayerScript("https://frame.example/assets/advertisement.js", frame, pages))
        assertFalse(AnimeWatchPlayback.isPlayerScript("https://advert.example/assets/index-DocunfmE.js", frame, pages))
        assertFalse(AnimeWatchPlayback.isPlayerScript(script, null, pages))
    }

    @Test fun requiresThePerResolutionNonceAndAnActualPlayerDocument() {
        val source = AnimeWatchPlayback.PlayerSource("https://cdn.example/master.m3u8?token=abc", "hls", frame)
        assertTrue(AnimeWatchPlayback.acceptsSource(nonce, nonce, source, pages))
        assertFalse(AnimeWatchPlayback.acceptsSource("wrong", nonce, source, pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, source.copy(referer = "https://advert.example/"), pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, source.copy(referer = "https://frame.example/ooa4/different"), pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, source.copy(url = "https://user:pass@cdn.example/master.m3u8"), pages))
    }

    @Test fun validatesTypedSourcesReportedFromTheChildFrame() {
        val source = AnimeWatchPlayback.PlayerSource("https://cdn.example/stream?token=abc", "hls", frame)
        assertTrue(AnimeWatchPlayback.acceptsSource(nonce, nonce, source, pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, source.copy(type = "text/html"), pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, source.copy(url = "blob:https://frame.example/id"), pages))
    }

    @Test fun acceptsAVerificationNoticeOnlyFromTheScopedPlayer() {
        val message = AnimeWatchPlayback.PlayerSource(referer = frame, error = "captcha_required")
        assertTrue(AnimeWatchPlayback.acceptsMessage(nonce, nonce, message, pages))
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, message, pages))
        assertFalse(AnimeWatchPlayback.acceptsMessage("wrong", nonce, message, pages))
        assertFalse(AnimeWatchPlayback.acceptsMessage(nonce, nonce, message.copy(referer = "https://advert.example/"), pages))
    }

    @Test fun discoversTheFrameFromTheTrustedParentWithoutAnInitialMetadataHint() {
        val source = AnimeWatchPlayback.PlayerSource(frame, "embed_frame", parent)
        val discovered = AnimeWatchPlayback.discoveredFrame(nonce, nonce, source, pages.first())
        assertEquals(AnimeWatchPlayback.publicHttps(frame), discovered)
        val sourceInFrame = AnimeWatchPlayback.PlayerSource("https://cdn.example/master.m3u8", "hls", frame)
        assertFalse(AnimeWatchPlayback.acceptsSource(nonce, nonce, sourceInFrame, pages.take(1)))
        assertTrue(AnimeWatchPlayback.acceptsSource(nonce, nonce, sourceInFrame, listOf(pages.first(), discovered!!)))
        assertTrue(AnimeWatchPlayback.isPlayerScript("https://frame.example/assets/index-DocunfmE.js", frame,
            listOf(pages.first(), discovered)))
    }

    @Test fun rejectsFrameDiscoveryFromOtherDocumentsOrForOtherVideos() {
        val source = AnimeWatchPlayback.PlayerSource(frame, "embed_frame", parent)
        assertNull(AnimeWatchPlayback.discoveredFrame("wrong", nonce, source, pages.first()))
        for (referer in listOf(frame, "https://advert.example/e/code", "https://host.example/e/another", "https://host.example/")) {
            assertNull(referer, AnimeWatchPlayback.discoveredFrame(nonce, nonce, source.copy(referer = referer), pages.first()))
        }
        for (url in listOf("https://frame.example/ooa4/another", "https://frame.example/advertisement", parent,
            "https://frame.example/api/videos/code/embed/details", "http://frame.example/ooa4/code",
            "https://user:pass@frame.example/ooa4/code", "https://localhost/ooa4/code", "https://127.0.0.1/ooa4/code")) {
            assertNull(url, AnimeWatchPlayback.discoveredFrame(nonce, nonce, source.copy(url = url), pages.first()))
        }
        assertNull(AnimeWatchPlayback.discoveredFrame(nonce, nonce, source.copy(type = "hls"), pages.first()))
        assertNull(AnimeWatchPlayback.discoveredFrame(nonce, nonce, source.copy(error = "captcha_required"), pages.first()))
    }

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
