package pl.ekinocloudstream

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class EkinoPlaybackTraceTest {
    private val detail = "https://ekino.ws/movie/show/PRIVATE_TITLE/33270?token=PRIVATE_QUERY"
    private val wrapper = "https://ekino.ws/watch/f/mix/PRIVATE_ENCRYPTED_ID"

    @Test fun recordsTheActualSequenceAndDistinguishesPlayerCaptchaFromCloudflare() {
        val trace = EkinoPlaybackTrace(false, false)
        trace.request(detail, "")
        trace.response(200, EkinoParser.document("<h1 class=title>Film</h1>"))
        trace.cookiesUpdated("", "PHPSESSID=PRIVATE_SESSION")
        trace.players(5, 10)
        trace.request(wrapper, "PHPSESSID=PRIVATE_SESSION")
        trace.response(200, EkinoParser.document("<div class=g-recaptcha data-sitekey=PRIVATE_KEY></div>"))
        trace.finish(EkinoPlaybackTrace.Outcome.ERROR, true)
        val report = trace.report()
        assertTrue(report.contains("#1 strona materiału: cookies=nie, PHP=nie"))
        assertTrue(report.contains("PHP po odpowiedzi=utworzona"))
        assertTrue(report.contains("#2 ramka mix: cookies=tak, PHP=tak"))
        assertTrue(report.contains("HTTP 200, CAPTCHA=reCAPTCHA"))
        assertFalse(report.contains("HTTP 200, Cloudflare"))
        assertTrue(report.contains("żądania wstrzymane=tak; żądania=2"))
        assertFalse(report.contains("PRIVATE"))
        assertFalse(report.contains("33270"))
    }

    @Test fun identifiesSessionRotationAndDoesNotTreatACookieAsProofOfVerification() {
        val trace = EkinoPlaybackTrace(true, true)
        trace.request(wrapper, "PHPSESSID=PRIVATE_OLD; cf_clearance=PRIVATE_CLEARANCE")
        trace.cookiesUpdated("PHPSESSID=PRIVATE_OLD", "PHPSESSID=PRIVATE_NEW")
        trace.response(403)
        trace.finish(EkinoPlaybackTrace.Outcome.ERROR, true)
        val report = trace.report()
        assertTrue(report.contains("UA=zapisany WebView"))
        assertTrue(report.contains("clearance=tak"))
        assertTrue(report.contains("PHP po odpowiedzi=zmieniona"))
        assertTrue(report.contains("HTTP 403"))
        assertFalse(report.contains("PRIVATE"))
    }

    @Test fun reportReadsNeverSendRequestsOrResumeAPausedGate() = runBlocking {
        val gate = EkinoRequestGate(true)
        val trace = EkinoPlaybackTrace(true, true)
        var requests = 0
        try { gate.request { requests++; trace.request(wrapper, "") } }
        catch (_: EkinoRequestGate.Paused) { }
        trace.finish(EkinoPlaybackTrace.Outcome.ERROR, gate.isPaused)
        repeat(5) { assertTrue(trace.report().contains("żądania=0")) }
        assertEquals(0, requests)
        assertFalse(trace.hasRequests())
        assertTrue(gate.isPaused)
    }

    @Test fun limitsOutputAndNeverIncludesBridgeTokensHtmlOrMediaPaths() {
        val trace = EkinoPlaybackTrace(true, false)
        trace.request("https://play.ekino.link/PRIVATE_BRIDGE?secret=PRIVATE_QUERY", "")
        trace.response(200, EkinoParser.document("<title>PRIVATE_TITLE</title><div class=cf-turnstile data-sitekey=PRIVATE_KEY></div>"))
        trace.host("https://video.example/PRIVATE_VIDEO?token=PRIVATE_TOKEN")
        repeat(100) { trace.embeds(1) }
        trace.response(429)
        val report = trace.report()
        assertTrue(report.contains("strona pośrednia"))
        assertTrue(report.contains("CAPTCHA=Turnstile"))
        assertTrue(report.contains("hosting=video.example"))
        assertFalse(report.contains("PRIVATE"))
        assertTrue(report.contains("HTTP 429"))
        assertTrue(report.lines().size <= 35)
    }
}
