package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateCatalogParserTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/animegate/$name.json")).readText()
    private val preview get() = fixture("catalog-preview")
    private val detail get() = fixture("anime-detail")

    @Test fun readsTheDoubleDataEnvelopeAndKeepsTheApiRatingScale() {
        val page = AnimeGateCatalogParser.parsePage(preview)
        assertEquals(3, page.total)
        assertEquals(1, page.page)
        assertEquals(8, page.limit)
        assertEquals(1, page.totalPages)
        assertFalse(page.hasNextPage)
        assertFalse(page.hasPrevPage)
        val anime = page.anime.single()
        assertEquals("anime-fixture-1", anime.id)
        assertEquals("fixture-anime", anime.slug)
        assertEquals("Fixture Anime", anime.title)
        assertEquals(83, anime.rating)
        assertEquals(2015, anime.year)
        assertEquals(12, anime.episodeCount)
        assertEquals(12, anime.totalEpisodes)
        assertEquals(listOf("Akcja"), anime.genres)
        assertEquals("https://images.example/cover.jpg", anime.coverImage)
    }

    @Test fun readsDetailsWithNullableFormatAndStringDuration() {
        val result = AnimeGateCatalogParser.parseDetails(detail, "fixture-anime")
        assertEquals("Syntetyczny opis do testu parsera.", result.synopsis)
        assertEquals(24, result.durationMinutes)
        assertEquals("16+", result.ageRating)
        assertEquals("FALL", result.season)
        assertEquals(21087, result.anilistId)
        assertEquals(listOf("Supermoce"), result.themes)
        assertEquals("https://images.example/banner.jpg", result.bannerImage)
        assertThrows(IllegalArgumentException::class.java) { AnimeGateCatalogParser.parseDetails(detail, "another-anime") }
    }

    @Test fun missingOrInvalidOptionalMetadataStaysUnknown() {
        val json = """{"data":{"id":"id","slug":"anime-","title":" A ","year":0,"effectiveRating":101,"episodeCount":null,"totalEpisodes":-1,"duration":"?","coverImage":"javascript:alert(1)","bannerImage":"https://user:password@images.example/a"}}"""
        val result = AnimeGateCatalogParser.parseDetails(json)
        assertEquals("A", result.anime.title)
        assertEquals("anime-", result.anime.slug)
        assertNull(result.anime.year)
        assertNull(result.anime.rating)
        assertNull(result.anime.episodeCount)
        assertNull(result.anime.totalEpisodes)
        assertNull(result.durationMinutes)
        assertNull(result.anime.coverImage)
        assertNull(result.bannerImage)
        assertTrue(result.anime.genres.isEmpty())
        assertTrue(result.themes.isEmpty())
    }

    @Test fun rejectsErrorsLoginHtmlAndOtherListEnvelopes() {
        for (body in listOf("<html>Login</html>", "{}", "null", """{"data":[]}""",
            preview.replace("\"success\": true", "\"success\": false"),
            preview.replace("\"code\": 200", "\"code\": 403"),
            preview.replace("\"code\": 200", "\"code\": 200, \"error\": \"denied\""),
            preview.replace("\"hasNextPage\": false", "\"hasNextPage\": null"))) {
            assertTrue(runCatching { AnimeGateCatalogParser.parsePage(body) }.isFailure)
        }
    }

    @Test fun keepsPaginationEvenWhenIndividualItemsAreInvalidOrDuplicated() {
        val item = """{"id":"id","slug":"anime","title":"Title"}"""
        val body = """{"data":{"data":[$item,$item,{"id":"bad","slug":"../auth/me","title":"Bad"},{"id":"missing-title","slug":"anime"}],"meta":{"total":30,"page":2,"limit":8,"totalPages":4,"hasNextPage":true,"hasPrevPage":true}}}"""
        val result = AnimeGateCatalogParser.parsePage(body)
        assertEquals(1, result.anime.size)
        assertEquals(2, result.page)
        assertEquals(4, result.totalPages)
        assertTrue(result.hasNextPage)
        assertTrue(result.hasPrevPage)
        val empty = body.replace("[$item,$item,{\"id\":\"bad\",\"slug\":\"../auth/me\",\"title\":\"Bad\"},{\"id\":\"missing-title\",\"slug\":\"anime\"}]", "[]")
        assertTrue(AnimeGateCatalogParser.parsePage(empty).anime.isEmpty())
    }

    @Test fun observedEpisodeShapeIsCompatibleWithTheExistingParser() {
        val episode = AnimeGateEpisodeParser.parse(fixture("episodes"), "anime-fixture-1").single()
        assertEquals("Odcinek 1", episode.displayName)
        assertEquals(listOf("link-fixture-1"), episode.linkIds)
    }
}
