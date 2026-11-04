package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateEpisodeParserTest {
    private fun row(id: String, number: String, title: String = "null", animeId: String = "anime-a") =
        """{"id":"$id","animeId":"$animeId","number":$number,"title":$title,"anime":{"id":"$animeId","title":"Fixture","slug":"fixture"},"links":[{"id":"group-a","source":"Fixture","order":1}]}"""

    @Test fun parsesTheConfirmedEnvelopeWithoutInventingVideoUrls() {
        val episode = AnimeGateEpisodeParser.parse("""{"success":true,"message":"ok","code":200,"data":[${row("episode-a", "1")}]}""").single()
        assertEquals("episode-a", episode.id)
        assertEquals("anime-a", episode.animeId)
        assertEquals("Odcinek 1", episode.displayName)
        assertEquals(listOf("group-a"), episode.linkIds)
    }
    @Test fun sortsFractionalAndSpecialEpisodesWithoutCollapsingDifferentIds() {
        val rows = listOf(row("two", "2"), row("one-b", "1"), row("fraction", "1.5"), row("special", "0"), row("one-a", "1"), row("negative", "-1"), row("precise", "1.0000000001"))
        val result = AnimeGateEpisodeParser.parse("""{"data":[${rows.joinToString(",")}]}""")
        assertEquals(listOf("negative", "special", "one-a", "one-b", "precise", "fraction", "two"), result.map { it.id })
        assertEquals("Odcinek 1.5", result.first { it.id == "fraction" }.displayName)
    }
    @Test fun bindsToTheRequestedAnimeDeduplicatesIdsAndKeepsTheExplicitTitle() {
        val rows = listOf(row("same", "1.00", "\" Test \""), row("same", "1"), row("other", "2", animeId = "anime-b"), """{"id":"broken","animeId":"anime-a","number":null}""")
        val result = AnimeGateEpisodeParser.parse("""{"data":[${rows.joinToString(",")}]}""", "anime-a")
        assertEquals(1, result.size)
        assertEquals("Test", result.single().displayName)
    }
    @Test fun rejectsErrorLoginAndUnknownEnvelopesInsteadOfReportingAnEmptyCatalogue() {
        for (body in listOf("<html>Login</html>", "{}", """{"data":{}}""", """{"success":false,"data":[]}""", """{"error":"denied","data":[]}""")) {
            assertTrue(runCatching { AnimeGateEpisodeParser.parse(body) }.isFailure)
        }
        assertTrue(AnimeGateEpisodeParser.parse("""{"success":true,"data":[]}""").isEmpty())
    }
}
