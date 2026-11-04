package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateCatalogApiTest {
    @Test fun allowsOnlyTheThreeCatalogueGetContracts() {
        val api = AnimeGateCatalogApi { url ->
            assertTrue(url, AnimeGateCatalogApi.isGetUrl(url))
            fixture(when { url.contains("/episodes/") -> "episodes"; url.contains("/slug/") -> "anime-detail"; else -> "catalog-preview" })
        }
        api.catalog("A&B #日本語")
        api.details("https://animegate.pl/anime/fixture-anime")
        api.episodes("anime-fixture-1")
        for (url in listOf("https://api.animegate.pl/api/v1/auth/me",
            "https://api.animegate.pl/api/v1/episodes/id/playback", "https://api.animegate.pl/api/v1/me/history",
            "https://api.animegate.pl/api/v1/anime/slug/title?token=x", "https://evil.example/api/v1/anime/slug/title",
            "https://api.animegate.pl/api/v1/episodes/anime/id?publicOnly=true&token=x",
            "https://api.animegate.pl/api/v1/anime/catalog-search?mode=preview&page=1&limit=8&search=x&sortBy=rating&sortOrder=desc&token=x")) {
            assertFalse(url, AnimeGateCatalogApi.isGetUrl(url))
        }
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/animegate/$name.json")).readText()

    @Test fun resolvesWatchPageToMatchingEpisodeAndLabel() {
        val api = AnimeGateCatalogApi { url -> fixture(if (url.contains("/episodes/")) "episodes" else "anime-detail") }
        val episode = api.watchEpisode("https://animegate.pl/watch/fixture-anime/1.0")
        assertEquals("episode-fixture-1", episode.id)
        assertEquals("Lektor", episode.links.single().label)
        assertThrows(IllegalArgumentException::class.java) { api.watchEpisode("https://animegate.pl/watch/fixture-anime/2") }
        assertThrows(IllegalArgumentException::class.java) { api.watchEpisode("https://evil.example/watch/fixture-anime/1") }
    }

    @Test fun encodesSearchAsOneParameterWithoutCreatingExtraRequests() {
        val requests = mutableListOf<String>()
        val api = AnimeGateCatalogApi { requests.add(it); fixture("catalog-preview") }
        assertEquals("Fixture Anime", api.catalog("A&B #日本語").anime.single().title)
        val uri = java.net.URI(requests.single())
        assertEquals("api.animegate.pl", uri.host)
        assertEquals("/api/v1/anime/catalog-search", uri.path)
        assertTrue(uri.rawQuery.contains("search=A%26B+%23%E6%97%A5%E6%9C%AC%E8%AA%9E"))
        assertNull(uri.rawFragment)
    }

    @Test fun usesDetailIdForThePublicEpisodeList() {
        val requests = mutableListOf<String>()
        val api = AnimeGateCatalogApi { url -> requests.add(url); fixture(if (url.contains("/episodes/")) "episodes" else "anime-detail") }
        val details = api.details("https://animegate.pl/anime/fixture-anime")
        assertEquals(1, api.episodes(details.anime.id).size)
        assertEquals(listOf("https://api.animegate.pl/api/v1/anime/slug/fixture-anime",
            "https://api.animegate.pl/api/v1/episodes/anime/anime-fixture-1?publicOnly=true"), requests)
    }

    @Test fun rejectsForeignOrMalformedTargetsBeforeTransport() {
        val api = AnimeGateCatalogApi { throw AssertionError("Transport must not be called") }
        for (url in listOf("https://evil.example/anime/title", "http://animegate.pl/anime/title",
            "https://animegate.pl/anime/../auth/me", "https://animegate.pl/anime/title?token=x",
            "https://animegate.pl/anime/title#x", "https://animegate.pl/watch/title/1")) {
            assertThrows(IllegalArgumentException::class.java) { api.details(url) }
        }
        assertThrows(IllegalArgumentException::class.java) { api.episodes("../auth/me") }
    }

    @Test fun rejectsDetailsAndEpisodesFromAnotherAnime() {
        val api = AnimeGateCatalogApi { fixture("anime-detail") }
        assertThrows(IllegalArgumentException::class.java) { api.details("https://animegate.pl/anime/other") }
        val episodes = AnimeGateCatalogApi { fixture("episodes") }.episodes("other")
        assertTrue(episodes.isEmpty())
    }
}
