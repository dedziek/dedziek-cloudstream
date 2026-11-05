package pl.ekinocloudstream

import org.junit.Assert.*
import org.junit.Test

class EkinoHostSourcesTest {
    private val page = "https://upzone.example/embed/test"

    @Test fun readsVideoElementsAndOrdinaryPlayerConfigurations() {
        val html = "<video><source src='https://cdn.example/content.m3u8?token=fixture' type='application/x-mpegURL'></video>" +
            "<script>jwplayer('player').setup({sources:[{file:'//cdn.example/movie.mp4'}]});</script>"
        val sources = EkinoHostSources.parse(html, page).media
        assertEquals(2, sources.size)
        assertTrue(sources[0].hls)
        assertFalse(sources[1].hls)
        assertTrue(sources.all { it.referer == page })
        assertEquals("https://cdn.example/movie.mp4", sources[1].url)
    }

    @Test fun opaqueSourceRequiresAnExplicitSupportedMediaType() {
        val html = "<script>var sources=[{file:'https://cdn.example/opaque',type:'hls'},{file:'https://cdn.example/not-media'}];</script>"
        val sources = EkinoHostSources.parse(html, page).media
        assertEquals(1, sources.size)
        assertEquals("https://cdn.example/opaque", sources[0].url)
        assertTrue(sources[0].hls)
    }

    @Test fun readsEscapedAndUnpackedMetadataWithoutExecutingScript() {
        val script = "var sources=[{file:'https:\\/\\/cdn.example\\/video.m3u8?x=1\\u0026y=2'}];"
        val sources = EkinoHostSources.parse("<html></html>", page, script).media
        assertEquals("https://cdn.example/video.m3u8?x=1&y=2", sources.single().url)
    }

    @Test fun rejectsNonMediaUnsafeAndPrivateTargets() {
        for (url in listOf("blob:https://cdn.example/x", "http://cdn.example/a.mp4", "javascript:alert(1)",
            "https://localhost/a.mp4", "https://127.0.0.1/a.mp4", "https://router.internal/a.mp4",
            "https://u:p@cdn.example/a.mp4", "https://cdn.example:9000/a.mp4", "https://ekino.ws/a.mp4")) {
            assertNull(url, EkinoHostSources.media(url, page))
        }
        assertNull(EkinoHostSources.media("https://cdn.example/page.html", page))
    }

    @Test fun recognisesNestedFramesAndIgnoresVerificationFrames() {
        val html = "<iframe data-src='//host.example/e/video'></iframe><iframe src='https://challenges.cloudflare.com/test'></iframe>"
        assertEquals(listOf("https://host.example/e/video"), EkinoHostSources.parse(html, page).frames)
        val gated = "<div class=cf-turnstile data-sitekey=fixture></div><video src='https://cdn.example/a.mp4'></video>"
        assertTrue(EkinoHostSources.parse(gated, page).media.isEmpty())
    }
}
