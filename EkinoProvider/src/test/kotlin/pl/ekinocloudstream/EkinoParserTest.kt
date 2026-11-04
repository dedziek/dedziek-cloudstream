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
}
