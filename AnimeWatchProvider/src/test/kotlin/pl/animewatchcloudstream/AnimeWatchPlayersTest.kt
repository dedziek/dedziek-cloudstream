package pl.animewatchcloudstream

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AnimeWatchPlayersTest {
    private val base = "https://animewatch.pl/example-odcinek-1/"
    private fun encoded(html: String) = Base64.getEncoder().encodeToString(html.toByteArray())

    @Test fun extractsActivePlayerAndDeduplicatesItsMirror() {
        val iframe = "<iframe src='https://bysedikamoum.com/e/abc123/show-episode-1'></iframe>"
        val html = "<div id='pembed'>$iframe</div><select class='mirror'><option value=''>Choose</option><option value='${encoded(iframe)}'>Byse</option></select>"
        assertEquals(listOf("https://bysedikamoum.com/e/abc123"), AnimeWatchPlayers.targets(Jsoup.parse(html), base))
    }

    @Test fun keepsOtherMirrorHostsAndProtocolRelativeUrls() {
        val html = "<select class='mirror'><option value='${encoded("<iframe src='//video.example/e/123'></iframe>")}'>Other</option></select>"
        assertEquals(listOf("https://video.example/e/123"), AnimeWatchPlayers.targets(Jsoup.parse(html), base))
    }

    @Test fun ignoresAdvertisementFramesAndMalformedMirrors() {
        val html = "<iframe src='https://ad.example/e/123'></iframe><select class='mirror'><option value='broken!'>No</option><option value='${encoded("<script>location='https://ad.example'</script>")}'>Ad</option></select>"
        assertTrue(AnimeWatchPlayers.targets(Jsoup.parse(html), base).isEmpty())
    }

    @Test fun rejectsUnsafeOrSiteTargets() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "http://127.0.0.1/video", "http://10.0.0.1/x", "http://[::1]/x", "https://a.local/video", "https://user@video.example/x", "https://video.example:444/x", "https://animewatch.pl/example/").forEach {
            assertNull(it, AnimeWatchPlayers.target(base, it))
        }
    }

    @Test fun extractsLazyFrameWithoutRewritingUnrelatedByseLikeDomains() {
        val html = "<div id='pembed'><iframe data-src='https://bysedikamoum.com.evil.example/e/code/title'></iframe></div>"
        assertEquals(listOf("https://bysedikamoum.com.evil.example/e/code/title"), AnimeWatchPlayers.targets(Jsoup.parse(html), base))
    }
}
