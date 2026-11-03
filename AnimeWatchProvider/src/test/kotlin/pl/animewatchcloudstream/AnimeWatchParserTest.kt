package pl.animewatchcloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeWatchParserTest {
    private val base = AnimeWatchParser.BASE
    private fun fixture(name: String) = javaClass.getResource("/animewatch/$name.html")!!.readText()

    @Test fun catalogDoesNotDuplicateNestedCardsOrTitleText() {
        val cards = AnimeWatchParser.cards(AnimeWatchParser.document(fixture("catalog"), "$base/anime/?order=update"), base)
        assertEquals(listOf("Sample & Friends", "Sample Film"), cards.map { it.title })
        assertEquals(listOf("TV", "Movie"), cards.map { it.type })
    }

    @Test fun catalogUsesLazyPosterAndCanonicalSiteUrls() {
        val cards = AnimeWatchParser.cards(AnimeWatchParser.document(fixture("catalog"), base), base)
        assertEquals("$base/wp-content/uploads/sample.jpg", cards.first().poster)
        assertEquals("$base/anime/sample/", cards.first().url)
    }

    @Test fun recommendationsExcludeCurrentSeries() {
        val detail = AnimeWatchParser.details(AnimeWatchParser.document(fixture("series"), base), "$base/anime/sample/")
        assertEquals(listOf("Recommended"), detail.recommendations.map { it.title })
    }

    @Test fun metadataUsesOriginalReleaseYearAndPolishLabels() {
        val detail = AnimeWatchParser.details(AnimeWatchParser.document(fixture("series"), base), "$base/anime/sample/")
        assertEquals("Sample Series", detail.title)
        assertEquals(2017, detail.year)
        assertEquals("TV", detail.type)
        assertEquals("Emitowane", detail.status)
        assertEquals(listOf("Action", "Fantasy"), detail.genres)
        assertEquals("Krótki opis serii.", detail.plot)
        assertEquals("$base/poster.jpg", detail.poster)
    }

    @Test fun episodesHaveNumericOrderDecimalsDeduplicationAndUnknownSpecials() {
        val detail = AnimeWatchParser.details(AnimeWatchParser.document(fixture("series"), base), "$base/anime/sample/")
        assertEquals(listOf(1.5, 2.0, 10.0, null), detail.episodes.map { it.episode })
        assertEquals(4, detail.episodes.size)
        assertTrue(detail.episodes.all { AnimeWatchParser.isSiteUrl(it.url) })
    }

    @Test fun movieUsesRealFilmPageRatherThanInventedEpisodeUrl() {
        val detail = AnimeWatchParser.details(AnimeWatchParser.document(fixture("movie"), base), "$base/anime/sample-film/")
        assertEquals("Movie", detail.type)
        assertEquals("$base/sample-film/", detail.episodes.single().url)
        assertEquals(1.0, detail.episodes.single().episode)
        assertEquals("$base/movie.jpg", detail.poster)
    }

    @Test fun absentEpisodesRemainEmpty() {
        val detail = AnimeWatchParser.details(AnimeWatchParser.document("<div class=infox><h1 class=entry-title>New</h1></div>", base), "$base/anime/new/")
        assertTrue(detail.episodes.isEmpty())
        assertNull(detail.year)
        assertNull(detail.poster)
    }

    @Test fun catalogPaginationPreservesQueryAndPath() {
        val url = "$base/anime/?order=update"
        assertEquals("$base/anime/?page=2&order=update", AnimeWatchParser.nextPage(AnimeWatchParser.document(fixture("catalog"), url), url))
    }

    @Test fun searchPaginationUsesWordPressPagePath() {
        val html = "<div class=pagination><a class='next page-numbers' href='/page/2/?s=sample'>Następne</a></div>"
        assertEquals("$base/page/2/?s=sample", AnimeWatchParser.nextPage(AnimeWatchParser.document(html, base), "$base/?s=sample"))
    }

    @Test fun paginationRejectsExternalNextPage() {
        val html = "<div class=hpage><a class=r href='https://advert.example/next/'>Next</a></div>"
        assertNull(AnimeWatchParser.nextPage(AnimeWatchParser.document(html, base), base))
    }

    @Test fun canonicalizationPreservesEscapedUnicodeAndRejectsUnsafeWebTargets() {
        assertEquals("$base/anime/ranma-%C2%BD/?a=1&b=2", AnimeWatchParser.absolute(base, "http://www.animewatch.pl/anime/ranma-%C2%BD/?a=1&b=2"))
        listOf("javascript:alert(1)", "file:///tmp/movie", "http://127.0.0.1/video", "http://localhost/movie", "https://user:pass@animewatch.pl/", "https://animewatch.pl:8443/").forEach {
            assertNull(it, AnimeWatchParser.absolute(base, it))
        }
    }

    @Test fun siteUrlRequiresCorrectHostAndHttps() {
        assertTrue(AnimeWatchParser.isSiteUrl("$base/anime/sample/"))
        assertFalse(AnimeWatchParser.isSiteUrl("http://animewatch.pl/"))
        assertFalse(AnimeWatchParser.isSiteUrl("https://animewatch.pl.advert.example/"))
        assertFalse(AnimeWatchParser.isSiteUrl("https://animewatch.pl@advert.example/"))
    }

    @Test fun challengeAndBomAreRecognizedWithoutTreatingChallengesAsEmptyCatalogs() {
        val doc = AnimeWatchParser.document("\uFEFF<title>Just a moment...</title><form id=challenge-form></form>", base)
        assertTrue(AnimeWatchParser.isChallenge(doc))
        assertFalse(AnimeWatchParser.isChallenge(AnimeWatchParser.document(fixture("catalog"), base)))
    }
}
