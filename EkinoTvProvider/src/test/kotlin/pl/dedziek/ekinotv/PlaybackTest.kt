package pl.dedziek.ekinotv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackTest {
    private val materialUrl = "https://ekino.ws/movie/show/test/42"
    private fun material(vararg languages: String): WebPage = WebPage(materialUrl, buildString {
        append("<div class=playerContainer><ul class=players>")
        for ((i, lang) in languages.withIndex()) append("<li><a href='#id$i-host$i'><i title='$lang'></i></a></li>")
        append("</ul>")
        for (i in languages.indices) append("<a onclick=\"ShowPlayer('host$i','id$i')\">Oglądaj</a>")
        append("</div>")
    })
    private class Browser : SiteBrowser {
        val visits = mutableListOf<String>()
        var gateOn: String? = null
        var bridgeLink = false
        override suspend fun page(url: String): WebPage = error("Playback must use the existing metadata")
        override suspend fun search(query: String): WebPage = error("No search during playback")
        override suspend fun userAgent() = "browser"
        override suspend fun player(material: String, player: Player): WebPage {
            visits.add(player.host)
            if (player.host == gateOn) throw SiteProblem("CAPTCHA")
            val host = if (bridgeLink) "play.ekino.link" else "host.example"
            return WebPage("https://ekino.ws/watch/f/${player.identity}", "<a target=_blank href='https://$host/e/${player.id}'>wideo</a>")
        }
        override suspend fun bridge(url: String): List<String> { visits.add("bridge"); return listOf("https://host.example/e/bridge") }
    }

    @Test fun stopsReadingEquivalentMirrorsAfterOneSourceButKeepsOtherLanguages() = runBlocking {
        val browser = Browser(); val emitted = mutableListOf<String>()
        Playback(browser, { url, referer -> HostResult(listOf(Media("$url/master.m3u8", referer, true))) }, {})
            .links(material("Lektor PL", " lektor   pl ", "Napisy PL")) { _, player -> emitted.add(player.host) }
        assertEquals(listOf("host0", "host2"), browser.visits)
        assertEquals(browser.visits, emitted)
    }

    @Test fun missingLanguageDoesNotCauseAllOtherUnknownMirrorsToLoadAfterSuccess() = runBlocking {
        val browser = Browser()
        Playback(browser, { url, referer -> HostResult(listOf(Media("$url/master.m3u8", referer, true))) }, {})
            .links(material("", " ", "")) { _, _ -> }
        assertEquals(listOf("host0"), browser.visits)
    }

    @Test fun equivalentPolishLabelsDoNotTriggerAnotherMirrorButSubtitlesStaySelectable() = runBlocking {
        val browser = Browser()
        Playback(browser, { url, referer -> HostResult(listOf(Media("$url/master.m3u8", referer, true))) }, {})
            .links(material("Lektor", "Lektor PL", "Lektor polski", "Napisy PL")) { _, _ -> }
        assertEquals(listOf("host0", "host3"), browser.visits)
    }

    @Test fun firstCaptchaPreventsAllHostingAndFurtherMirrorRequests() = runBlocking {
        val browser = Browser().apply { gateOn = "host0" }
        try {
            Playback(browser, { _, _ -> error("Must not request hosting after CAPTCHA") }, {}).links(material("PL", "EN")) { _, _ -> fail() }
            fail("CAPTCHA must be reported")
        } catch (_: SiteProblem) { }
        assertEquals(listOf("host0"), browser.visits)
    }

    @Test fun aLaterCaptchaPreservesAlreadyEmittedVideoAndStopsTheRemainingMirrors() = runBlocking {
        val browser = Browser().apply { gateOn = "host1" }; var count = 0
        assertTrue(Playback(browser, { url, referer -> HostResult(listOf(Media("$url/master.m3u8", referer, true))) }, {})
            .links(material("PL", "EN", "DE")) { _, _ -> count++ })
        assertEquals(1, count); assertEquals(listOf("host0", "host1"), browser.visits)
    }

    @Test fun hostingDenialStopsTheOperationWithoutTryingAnotherMirror() = runBlocking {
        val browser = Browser(); var calls = 0
        try {
            Playback(browser, { _, _ -> calls++; HostResult(message = "HTTP 429", blocked = true) }, {})
                .links(material("PL", "EN")) { _, _ -> fail() }
            fail("Expected hosting denial")
        } catch (_: SiteProblem) { }
        assertEquals(1, calls); assertEquals(listOf("host0"), browser.visits)
    }

    @Test fun cancellationNeverStartsAnotherMirror() = runBlocking {
        val browser = Browser()
        try {
            Playback(browser, { _, _ -> throw CancellationException("test") }, {}).links(material("PL", "EN")) { _, _ -> fail() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf("host0"), browser.visits)
    }

    @Test fun publicBridgeUsesTheBrowserAndOnlyItsExternalDestinationGoesToTheHostingClient() = runBlocking {
        val browser = Browser().apply { bridgeLink = true }; val requests = mutableListOf<String>()
        Playback(browser, { url, referer -> requests.add(url); HostResult(listOf(Media("$url/video.mp4", referer, false))) }, {})
            .links(material("PL")) { _, _ -> }
        assertEquals(listOf("host0", "bridge"), browser.visits)
        assertEquals(listOf("https://host.example/e/bridge"), requests)
    }
}
