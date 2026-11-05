package pl.dedziek.ekinotv

import org.junit.Assert.*
import org.junit.Test

class CatalogueTest {
    private val movie = "https://ekino.ws/movie/show/proba/42"
    private fun fixture(name: String) = WebPage(movie, javaClass.getResource("/site/$name.html")!!.readText())

    @Test fun catalogueCombinesDuplicateAnchorsAndKeepsPolishMetadata() {
        val cards = Catalogue.cards(fixture("search"))
        assertEquals(2, cards.size)
        assertEquals("Film & próba", cards[0].title)
        assertEquals(2024, cards[0].year)
        assertEquals("https://ekino.ws/static/thumb/movie.jpg", cards[0].poster)
        assertTrue(cards[1].series)
    }

    @Test fun episodesAreSortedAndNeitherDuplicateNorForeignEpisodesAreReturned() {
        val material = Catalogue.material(fixture("series"))
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 10), material.episodes.map { it.season to it.number })
        assertEquals("Serial próbny", material.title)
        assertEquals("Opis testowy.", material.plot)
    }

    @Test fun readsOnlyAdvertisedFreePlayerControlsIncludingImmediateVariant() {
        assertEquals(listOf("player", "upzone"), Catalogue.players(fixture("players")).map { it.host })
        assertEquals(5, Catalogue.players(fixture("players-immediate")).size)
        assertEquals("Lektor PL", Catalogue.players(fixture("players")).first().language)
    }

    @Test fun acceptsOnlyOfficialHttpsPagesWithoutChangingTheirOrigins() {
        assertTrue(Site.official("https://ekino-tv.pl/"))
        assertTrue(Site.material(movie))
        for (url in listOf("http://ekino.ws/", "https://ekino.ws.evil.example/", "https://x@ekino.ws/", "https://ekino.ws:8443/"))
            assertFalse(url, Site.official(url))
        assertEquals("https://ekino-tv.pl/movie/show/proba/42", Site.absolute(Site.ENTRY, "/movie/show/proba/42"))
        assertEquals("https://ekino.ws/serie/watch/proba+season%5B1%5D+episode%5B2%5D+",
            Site.absolute(movie, "/serie/watch/proba+season[1]+episode[2]+"))
        for (url in listOf("https://127.0.0.1/x", "https://localhost/x", "https://a.local/x", "javascript:alert(1)", "file:///x"))
            assertNull(Site.absolute(movie, url))
    }

    @Test fun loginCaptchaDoesNotMarkCatalogueAsPlayerChallenge() {
        val login = "<form id=login_fr><div class=g-recaptcha data-sitekey=public></div></form><h1 class=title>Film</h1>"
        assertNull(BrowserSnapshot(WebPage(movie, login)).gate())
        val wrapper = WebPage("https://ekino.ws/watch/f/player/abc", "<script>turnstile.render('#c',{sitekey:'public'})</script>")
        assertEquals("CAPTCHA odtwarzacza", BrowserSnapshot(WebPage(movie, login), listOf(wrapper)).gate())
        assertEquals("Cloudflare", BrowserSnapshot(WebPage(movie, "<title>Just a moment...</title>")).gate())
    }

    @Test fun greenWidgetAndPublicMaterialAreInsufficientToVerifyAPlayer() {
        val page = WebPage(movie, "<h1 class=title>Film</h1><div class=playerContainer></div>")
        assertTrue(BrowserSnapshot(page).verified(false))
        assertFalse(BrowserSnapshot(page).verified(true))
        val gate = WebPage("https://ekino.ws/watch/f/player/abc", "<div class=cf-turnstile data-status=success></div>")
        assertFalse(BrowserSnapshot(page, listOf(gate)).verified(true))
        val ready = WebPage(gate.url, "<a target=_blank href='https://host.example/e/abc'>Oglądaj</a>")
        assertTrue(BrowserSnapshot(page, listOf(ready)).verified(true))
    }

    @Test fun wrapperEmbedsExcludePremiumAndVerificationFrames() {
        val page = WebPage("https://ekino.ws/watch/f/player/a", """<iframe src='https://challenges.cloudflare.com/x'></iframe>
            <a target=_blank href='https://host.example/e/a'>wideo</a><div id=premium><iframe src='https://paid.example/e/a'></iframe></div>""")
        assertEquals(listOf("https://host.example/e/a"), Catalogue.embeds(page))
    }

    @Test fun paginationOnlyRecognizesTheNextPageInTheSameCategory() {
        val html = "<div id=pager><a href='/movie/cat/strona[3]+'>3</a><a href='/movie/cat/strona[2]+'>2</a></div>"
        assertTrue(Catalogue.hasNext(WebPage("https://ekino.ws/movie/cat/", html)))
        assertFalse(Catalogue.hasNext(WebPage("https://ekino.ws/movie/cat/strona%5B3%5D+", html)))
    }
}
