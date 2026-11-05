package pl.ekinocloudstream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EkinoHostResolverTest {
    private val page = "https://upzone.example/e/fixture?token=PRIVATE_FIXTURE"

    @Test fun unsupportedHostWithStaticPlaylistReturnsMediaAndKeepsReferer() = runBlocking {
        val media = mutableListOf<EkinoHostSources.Media>()
        val requests = mutableListOf<Pair<String, String>>()
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { url, referer ->
            requests.add(url to referer)
            EkinoHostResolver.Response(200, "<script>var sources=[{file:'https://cdn.example/video.m3u8'}];</script>")
        }, { media.add(it) })
        resolver.resolve(page, "https://play.ekino.link/parent")
        assertEquals(listOf(page to "https://play.ekino.link/parent"), requests)
        assertEquals("https://cdn.example/video.m3u8", media.single().url)
        assertEquals(page, media.single().referer)
        assertFalse(resolver.report().contains("PRIVATE_FIXTURE"))
        assertFalse(resolver.report().contains("/e/"))
    }

    @Test fun recognisedExtractorReturningNoVideoIsNotRetriedWithAnotherClient() = runBlocking {
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.EMPTY }, { _, _ ->
            error("Recognised extractor must not trigger another request")
        }, { error("No source expected") })
        resolver.resolve(page, "https://ekino.ws/")
        assertTrue(resolver.report().contains("ekstraktor bez wideo"))
    }

    @Test fun nestedHostingUsesItsExtractorBeforeFetchingAnyMoreHtml() = runBlocking {
        val extracted = mutableListOf<String>()
        var fetches = 0
        val resolver = EkinoHostResolver({ url, _ ->
            if (url.startsWith("https://host.example/")) { extracted.add(url); EkinoHostResolver.Dispatch.FOUND }
            else EkinoHostResolver.Dispatch.UNSUPPORTED
        }, { _, _ -> fetches++; EkinoHostResolver.Response(200, "<iframe src='https://host.example/e/video'></iframe>") }, {})
        resolver.resolve(page, "https://ekino.ws/")
        assertEquals(1, fetches)
        assertEquals(listOf("https://host.example/e/video"), extracted)
    }

    @Test fun accessDenialStopsFurtherReadsToThatHost() = runBlocking {
        var fetches = 0
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            fetches++; EkinoHostResolver.Response(403)
        }, {})
        resolver.resolve(page, "https://ekino.ws/")
        resolver.nextPlayer()
        resolver.resolve("https://upzone.example/e/other", "https://ekino.ws/")
        assertEquals(1, fetches)
        assertTrue(resolver.report().contains("HTTP 403"))
        assertEquals(EkinoHostResolver.Verification.ACCESS_DENIED, resolver.verification)
    }

    @Test fun theSameHostingCanEmitForDifferentPlayerLanguageLabels() = runBlocking {
        var extracted = 0
        val resolver = EkinoHostResolver({ _, _ -> extracted++; EkinoHostResolver.Dispatch.FOUND },
            { _, _ -> error("Built-in extractor already found a source") }, {})
        resolver.resolve(page, "https://ekino.ws/")
        resolver.resolve(page, "https://ekino.ws/")
        assertEquals(1, extracted)
        resolver.nextPlayer()
        resolver.resolve(page, "https://ekino.ws/")
        assertEquals(2, extracted)
    }

    @Test fun verificationIsReportedWithoutFollowingFramesOrInvokingUnpack() = runBlocking {
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            EkinoHostResolver.Response(200, "<div class=cf-turnstile data-sitekey=x></div><iframe src='https://host.example/e/video'></iframe>")
        }, { error("No source expected") }, { error("Challenge must not be processed as a packed player") })
        resolver.resolve(page, "https://ekino.ws/")
        assertTrue(resolver.report().contains("weryfikacja wymagana"))
        assertEquals(EkinoHostResolver.Verification.CAPTCHA, resolver.verification)
    }

    @Test fun cloudflareInterstitialIsDistinguishedFromAPlayerCaptcha() = runBlocking {
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            EkinoHostResolver.Response(200, "<title>Just a moment...</title><form id='challenge-form'></form>")
        }, { error("No source expected") }, { error("Challenge must not be processed") })
        resolver.resolve(page, "https://ekino.ws/")
        assertEquals(EkinoHostResolver.Verification.CLOUDFLARE, resolver.verification)
    }

    @Test fun redirectsAndDuplicateFramesAreBoundedAndSecretsStayOutOfReports() = runBlocking {
        var fetches = 0
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            fetches++
            EkinoHostResolver.Response(200, "<iframe src='$page'></iframe><iframe src='http://127.0.0.1/private'></iframe>")
        }, {})
        resolver.resolve(page, "https://ekino.ws/")
        assertEquals(1, fetches)
        assertFalse(resolver.report().contains("PRIVATE_FIXTURE"))
    }

    @Test fun cancellationIsPropagatedAndNotConvertedToAHostingFailure() = runBlocking {
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            throw CancellationException("fixture cancellation")
        }, {})
        try { resolver.resolve(page, "https://ekino.ws/"); fail("Expected cancellation") }
        catch (error: CancellationException) { assertEquals("fixture cancellation", error.message) }
    }

    @Test fun byseFrontendUsesItsApiOnceAndKeepsHeadersOnTheEmittedMedia() = runBlocking {
        val media = EkinoHostSources.Media("https://cdn.example/master.m3u8", true, page, mapOf("Origin" to "https://upzone.example"))
        val emitted = mutableListOf<EkinoHostSources.Media>()
        var calls = 0
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            EkinoHostResolver.Response(200, "<title>Byse Frontend</title><div id=root></div>")
        }, { emitted.add(it) }, byse = {
            calls++; EkinoByseResolver.Result(listOf(media))
        })
        val embed = "https://upzone.example/e/fixture"
        resolver.resolve(embed, "https://ekino.ws/")
        resolver.resolve(embed, "https://ekino.ws/")
        assertEquals(1, calls)
        assertEquals(listOf(media), emitted)
        assertTrue(resolver.report().contains("Byse API OK"))
    }

    @Test fun unavailableAndQuotaPagesDoNotTriggerUnpackOrApiCalls() = runBlocking {
        for ((html, reason) in listOf(
            "<div>Transfer dla darmowych użytkowników został wyczerpany i jest dostępny dla użytkowników PREMIUM.</div>" to "wyczerpany transfer darmowy",
            "<title>Video not found | DoodStream</title>" to "plik niedostępny")) {
            val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ -> EkinoHostResolver.Response(200, html) },
                { error("No source expected") }, { error("Unavailable page is not a packed player") }, byse = { error("No API expected") })
            resolver.resolve(page, "https://ekino.ws/")
            assertTrue(resolver.report().contains(reason))
        }
    }

    @Test fun dnsFailuresAreReportedWithoutTheExceptionsUrlOrSessionData() = runBlocking {
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            throw java.net.UnknownHostException("PRIVATE_FIXTURE")
        }, {})
        resolver.resolve(page, "https://ekino.ws/")
        assertTrue(resolver.report().contains("błąd DNS"))
        assertFalse(resolver.report().contains("PRIVATE_FIXTURE"))
    }

    @Test fun byseDenialStopsOtherPlayersFromFetchingTheSameHostAgain() = runBlocking {
        var fetches = 0; var apiCalls = 0
        val resolver = EkinoHostResolver({ _, _ -> EkinoHostResolver.Dispatch.UNSUPPORTED }, { _, _ ->
            fetches++; EkinoHostResolver.Response(200, "<title>Byse Frontend</title><div id=root></div>")
        }, { error("No source expected") }, byse = {
            apiCalls++; EkinoByseResolver.Result(status = 403)
        })
        resolver.resolve("https://byse.example/e/first", "https://ekino.ws/")
        resolver.nextPlayer()
        resolver.resolve("https://byse.example/e/second", "https://ekino.ws/")
        assertEquals(1, fetches)
        assertEquals(1, apiCalls)
        assertEquals(EkinoHostResolver.Verification.ACCESS_DENIED, resolver.verification)
    }
}
