package pl.ekinocloudstream

import org.junit.Assert.*
import org.junit.Test

class EkinoParserTest {
    private val base = EkinoParser.BASE
    private fun doc(html: String) = EkinoParser.document(html)
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/ekino/$name.html")).readText()

    @Test fun parsesMovieAndSeriesSearchCardsAndDeduplicatesTitleLinks() {
        val cards = EkinoParser.cards(doc(fixture("search")))
        assertEquals(2, cards.size)
        assertEquals("Film & próba", cards[0].title)
        assertEquals("$base/static/thumb/movie.jpg", cards[0].poster)
        assertEquals(2024, cards[0].year)
        assertFalse(cards[0].series)
        assertTrue(cards[1].series)
        assertEquals("Serial próbny", cards[1].title)
    }

    @Test fun parsesHomeListWithoutRequiringSearchLayout() {
        val html = "<ul class=list><li><div class=scope_left><a href='/movie/show/proba/12'><img src='//ekino.ws/a.jpg'></a></div>" +
            "<div class=title><a href='/movie/show/proba/12'>Pełny tytuł</a></div><p class=cates>1998 | HD</p></li></ul>"
        val card = EkinoParser.cards(doc(html)).single()
        assertEquals("Pełny tytuł", card.title)
        assertEquals(1998, card.year)
        assertEquals("$base/a.jpg", card.poster)
    }

    @Test fun seriesSliderUsesFullTitleAndPoster() {
        val card = EkinoParser.cards(doc("<a href='/serie/show/serial' title='Oglądaj: Serial próbny'><img src='/poster.jpg'></a>")).single()
        assertTrue(card.series)
        assertEquals("Serial próbny", card.title)
        assertEquals("$base/poster.jpg", card.poster)
    }

    @Test fun laterCatalogueCardFillsPosterMissingFromSidebarLink() {
        val html = "<a href='/serie/show/serial'>Serial próbny</a><div class=movies-list-item>" +
            "<a href='/serie/show/serial'><img src='/poster.jpg'></a><div class=title><a href='/serie/show/serial'>Serial próbny</a></div></div>"
        val card = EkinoParser.cards(doc(html)).single()
        assertEquals("Serial próbny", card.title)
        assertEquals("$base/poster.jpg", card.poster)
    }

    @Test fun ignoresEpisodeLinksAdsAndForeignCatalogEntries() {
        val html = "<a href='/serie/watch/test+season[1]+episode[2]'>s1e2</a>" +
            "<a href='https://ekino.ws.evil.example/movie/show/test/1'>Obcy</a><a href='/premium/'>Premium</a>"
        assertTrue(EkinoParser.cards(doc(html)).isEmpty())
    }

    @Test fun resolvesBracketPathsAndNormalizesOnlyTheVerifiedLegacyDomain() {
        assertEquals("$base/serie/watch/test+season%5B2%5D+episode%5B3%5D+",
            EkinoParser.siteUrl(base, "/serie/watch/test+season[2]+episode[3]+"))
        assertEquals("$base/movie/show/test/2", EkinoParser.siteUrl(base, "https://ekino-tv.pl/movie/show/test/2"))
        assertNull(EkinoParser.siteUrl(base, "https://ekino-tv.link/movie/show/test/2"))
    }

    @Test fun savedCookieScopeRejectsLegacyDomainsPortsCredentialsAndSuffixes() {
        assertTrue(EkinoParser.isSiteUrl(base))
        assertTrue(EkinoLoginPolicy.isEkinoUrl("$base/login"))
        for (url in listOf("http://ekino.ws/", "https://ekino.ws:444/", "https://ekino-tv.pl/",
            "https://ekino.ws.evil.example/", "https://user@ekino.ws/", "https://ekino.ws./")) {
            assertFalse(url, EkinoParser.isSiteUrl(url))
            assertFalse(url, EkinoLoginPolicy.isEkinoUrl(url))
        }
    }

    @Test fun ignoresUnsafeEmbedDestinations() {
        for (url in listOf("javascript:alert(1)", "file:///tmp/a", "data:text/html,x", "http://127.0.0.1/a",
            "https://[::1]/a", "https://router.local/a", "https://user:pass@video.example/a", "https://video.example:9000/a")) {
            assertNull(url, EkinoParser.absolute(base, url))
        }
    }

    @Test fun followsOnlyTheNextPageInTheSameMovieCategory() {
        val html = "<div id=pager><a href='/movie/cat/strona[5]+'>5</a><a href='/movie/cat/strona[3]+'>3</a>" +
            "<a href='https://ad.example/movie/cat/strona[3]+'>Obcy</a></div>"
        assertEquals("$base/movie/cat/strona%5B3%5D+", EkinoParser.nextPage(doc(html), "$base/movie/cat/strona%5B2%5D+"))
        assertNull(EkinoParser.nextPage(doc(html), "$base/movie/cat/strona%5B5%5D+"))
    }

    @Test fun parsesRealSeasonUrlSyntaxSortsAndDeduplicatesEpisodes() {
        val details = EkinoParser.details(doc(fixture("series")), "$base/serie/show/serial")
        assertEquals("Serial próbny", details.title)
        assertEquals("Opis testowy.", details.plot)
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 10), details.episodes.map { it.season to it.episode })
        assertEquals("Specjalny", details.episodes[0].title)
        assertTrue(details.episodes.all { EkinoParser.isDetailUrl(it.url) })
    }

    @Test fun parsesFilmMetadataFromSidebarWithoutMistakingUploadDateForYear() {
        val html = "<h1 class=title>Film próbny</h1><img class=moviePoster src='/static/normal/a.jpg'>" +
            "<div class=infoContent>Dodano 2026-11-04</div><div class=catBox><div class=cat><a href=''>1996</a> | " +
            "<a href='/movie/cat/kategoria[1]+'>Akcja</a></div></div>"
        val details = EkinoParser.details(doc(html), "$base/movie/show/film/1")
        assertEquals(1996, details.year)
        assertEquals(listOf("Akcja"), details.tags)
        assertTrue(details.episodes.isEmpty())
    }

    @Test fun buildsOnlyAdvertisedFreePlayersAndPreservesLanguageLabels() {
        val html = fixture("players")
        val players = EkinoParser.players(doc(html), "$base/movie/show/film/1")
        assertEquals(2, players.size)
        assertEquals("$base/watch/f/player/AbCd_123", players[0].url)
        assertEquals("Lektor PL", players[0].language)
        assertEquals("Napisy PL", players[1].language)
        assertEquals(10, EkinoParser.playerDelaySeconds(doc(html)))
    }

    @Test fun recognizesFiveImmediateFreePlayersWithoutInventingACountdown() {
        val html = fixture("players-immediate")
        val players = EkinoParser.players(doc(html), "$base/movie/show/the-list-hd-2023-lektor/33512")
        assertEquals(0, EkinoParser.playerDelaySeconds(doc(html)))
        assertEquals(listOf("player", "dood", "hqq", "mix", "upzone"), players.map { it.host })
        assertTrue(players.all { it.language == "Lektor PL" })
        assertEquals("$base/watch/f/player/TestPlayer", players[0].url)
        assertEquals(5, EkinoParser.players(doc(html.replace("function ShowPlayer(", "function  ShowPlayer (")), base).size)
    }

    @Test fun preservesCountdownAndRejectsUnknownTimingInsteadOfTreatingItAsImmediate() {
        assertEquals(10, EkinoParser.playerDelaySeconds(doc(fixture("players"))))
        assertNull(EkinoParser.playerDelaySeconds(doc(fixture("players").replace("time = 10;", "time = configuredDelay;"))))
        val unknown = fixture("players-immediate").replace("$('#over-player').hide();", "setTimeout(startPlayer, 5000);")
        assertNull(EkinoParser.playerDelaySeconds(doc(unknown)))
        assertTrue(EkinoParser.players(doc(unknown), base).isEmpty())
    }

    @Test fun immediatePlayersStillRequireTheRouteInTheShowPlayerScriptItself() {
        val html = fixture("players-immediate").replace("/watch/f/", "/other/") + "<script>var unrelated = '/watch/f/';</script>"
        assertNull(EkinoParser.playerDelaySeconds(doc(html)))
        assertTrue(EkinoParser.players(doc(html), base).isEmpty())
    }

    @Test fun refusesToInventPlayerRouteWhenPageScriptChanges() {
        val html = fixture("players").replace("/watch/f/", "/new-player/")
        assertTrue(EkinoParser.players(doc(html), base).isEmpty())
        assertTrue(EkinoParser.players(doc(fixture("players").replace("time = 10;", "time = 100;")), base).isEmpty())
    }

    @Test fun extractsPublicEmbedsAndDirectVideoButExcludesPremiumContainer() {
        val html = "<iframe src='https://host.example/e/123'></iframe>" +
            "<a class=buttonprch href='//other.example/e/456'>Oglądaj</a>" +
            "<video><source src='https://media.example/test.m3u8'></video>" +
            "<div id=premium><iframe src='https://premium.example/test'></iframe></div>" +
            "<iframe src='/login'></iframe><iframe src='about:blank'></iframe>"
        val embeds = EkinoParser.embeds(doc(html), "$base/watch/f/player/abc")
        assertEquals(3, embeds.size)
        assertEquals(listOf(false, false, true), embeds.map { it.direct })
    }

    @Test fun headerLoginAndHiddenPremiumWarningDoNotHideAnonymousCatalog() {
        val html = "<form id=login_fr action=/login><input type=password></form><h1 class=title>Film</h1>" +
            "<div id=premium>Przepraszamy! Zarejestruj się lub zaloguj.</div>"
        assertFalse(EkinoParser.isLoginPage(doc(html)))
        assertTrue(EkinoParser.isLoginPage(doc("<title>Logowanie - Ekino</title>")))
    }

    @Test fun followsPublicWatchLinkThroughBridgeAndKeepsTheHostingReferer() {
        val watch = "$base/watch/f/player/TestId"
        val outer = doc("<a href='https://play.ekino.link/advertised' target='_blank'>Oglądaj</a>")
        val first = EkinoParser.embeds(outer, watch).single()
        assertTrue(EkinoParser.isBridgeUrl(first.url))
        assertEquals(watch, first.referer)
        val inner = doc("<iframe src='//host.example/e/video'></iframe>")
        val host = EkinoParser.embeds(inner, first.url).single()
        assertEquals("https://host.example/e/video", host.url)
        assertEquals(first.url, host.referer)
        assertFalse(EkinoParser.isSiteUrl(first.url))
    }

    @Test fun plainWindowLinksAreOnlyParsedFromFreeWatchWrappers() {
        val html = "<a href='https://host.example/e/test' target='_blank'>Oglądaj</a>" +
            "<div id=premium><a href='https://premium.example/test' target='_blank'>Premium</a></div>" +
            "<a href='/login' target='_blank'>Zaloguj</a>"
        assertTrue(EkinoParser.embeds(doc(html), "$base/movie/show/test/1").isEmpty())
        assertEquals(listOf("https://host.example/e/test"), EkinoParser.embeds(doc(html), "$base/watch/f/player/test").map { it.url })
    }

    @Test fun bridgeScopeIsSeparateAndRejectsLookalikesAndInsecureOrigins() {
        assertTrue(EkinoParser.isBridgeUrl("https://play.ekino.link/test"))
        for (url in listOf("http://play.ekino.link/", "https://play.ekino.link.evil.example/",
            "https://user@play.ekino.link/", "https://play.ekino.link:444/", "https://ekino.link/")) {
            assertFalse(url, EkinoParser.isBridgeUrl(url))
        }
    }

    @Test fun identifiesInteractivePlayerVerificationWithoutExecutingIt() {
        assertTrue(EkinoParser.isPlayerVerification(doc("<div class=cf-turnstile data-sitekey=fixture></div>")))
        assertTrue(EkinoParser.isPlayerVerification(doc("<script>turnstile.render('#verify', {sitekey:'fixture'});</script>")))
        assertFalse(EkinoParser.isPlayerVerification(doc(fixture("players-immediate"))))
    }

    @Test fun distinguishesCloudflareDenialFromOrdinaryPageScripts() {
        assertTrue(EkinoParser.isChallenge(doc("<title>Attention Required! | Cloudflare</title>")))
        assertTrue(EkinoParser.isChallenge(doc("<div id=cf-error-details>Blocked</div>")))
        assertFalse(EkinoParser.isChallenge(doc(fixture("players"))))
    }

    @Test fun verificationFramesDoNotAcquireAuthenticatedOriginPrivileges() {
        for (url in listOf("https://challenges.cloudflare.com/a", "https://www.google.com/recaptcha/api2/anchor", "about:blank")) {
            assertTrue(url, EkinoLoginPolicy.allowLegacyNavigation(url))
            assertFalse(url, EkinoLoginPolicy.isEkinoUrl(url))
        }
        assertFalse(EkinoLoginPolicy.allowLegacyNavigation("https://www.google.com/search?q=test"))
        assertFalse(EkinoLoginPolicy.allowLegacyNavigation("https://challenges.cloudflare.com.evil.example/"))
    }

    @Test fun manualVerificationRetainsOnlyThePublicMovieOrEpisodePage() {
        assertEquals("https://ekino.ws/movie/show/rescued-hd-2026-napisy/43324",
            EkinoLoginPolicy.verificationPage("https://ekino.ws/movie/show/rescued-hd-2026-napisy/43324?token=PRIVATE_FIXTURE#player"))
        assertEquals("https://ekino.ws/serie/watch/test+season%5B1%5D+episode%5B2%5D",
            EkinoLoginPolicy.verificationPage("https://ekino.ws/serie/watch/test+season%5B1%5D+episode%5B2%5D"))
    }

    @Test fun manualVerificationNeverReusesEncryptedWrappersOrForeignTargets() {
        for (url in listOf("https://ekino.ws/watch/f/upzone/PRIVATE_FIXTURE", "https://ekino.ws/login",
            "http://ekino.ws/movie/show/test/1", "https://ekino.ws.evil.example/movie/show/test/1",
            "https://user:password@ekino.ws/movie/show/test/1", "https://play.ekino.link/movie/show/test/1",
            "https://ekino.ws:444/movie/show/test/1", "javascript:alert(1)")) {
            assertNull(url, EkinoLoginPolicy.verificationPage(url))
        }
    }
}
