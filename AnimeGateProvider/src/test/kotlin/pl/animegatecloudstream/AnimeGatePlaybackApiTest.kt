package pl.animegatecloudstream

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Test

class AnimeGatePlaybackApiTest {
    private val ticket = """{"success":true,"message":"Resource created successfully","code":201,"data":{"ticket":"synthetic-ticket","expiresIn":60}}"""
    private fun resolved(url: String = "https://playmogo.com/e/fixture123") =
        """{"success":true,"code":201,"data":{"iframeUrl":"$url"}}"""

    @Test fun exchangesLinkForFreshTicketAndThenIframe() {
        val requests = mutableListOf<Pair<String, String>>()
        val api = AnimeGatePlaybackApi(post = { url, body -> requests.add(url to body); if (url.endsWith("/resolve")) resolved() else ticket })
        assertEquals("https://playmogo.com/e/fixture123", api.resolve("episode-1", "link-1"))
        assertEquals(listOf("https://api.animegate.pl/api/v1/episodes/episode-1/playback", "https://api.animegate.pl/api/v1/episodes/episode-1/playback/resolve"), requests.map { it.first })
        assertEquals("link-1", ObjectMapper().readTree(requests[0].second).path("linkId").asText())
        assertEquals("synthetic-ticket", ObjectMapper().readTree(requests[1].second).path("ticket").asText())
        api.resolve("episode-1", "link-2")
        assertEquals(4, requests.size) // no cached ticket or iframe across versions/clicks
    }

    @Test fun doesNotResolveExpiredTicketIncludingNetworkTime() {
        var now = 1000L
        val requests = mutableListOf<String>()
        val api = AnimeGatePlaybackApi(post = { url, _ -> requests.add(url); now += 60_000; ticket }, clock = { now })
        assertThrows(IllegalArgumentException::class.java) { api.resolve("episode", "link") }
        assertEquals(1, requests.size)
    }

    @Test fun rejectsErrorsMissingAndInvalidTicketsBeforeResolve() {
        for (body in listOf("<html>Login</html>", "{}", ticket.replace("true", "false"),
            ticket.replace("201", "403"), ticket.replace("synthetic-ticket", ""),
            ticket.replace("60", "0"), ticket.replace("60", "-1"), ticket.replace("60", "60.5"),
            ticket.replace("60", "\"60\""), ticket.replace("60", "2147483648"))) {
            var count = 0
            val api = AnimeGatePlaybackApi(post = { _, _ -> count++; body })
            assertTrue(runCatching { api.resolve("episode", "link") }.isFailure)
            assertEquals(1, count)
        }
    }

    @Test fun rejectsUnknownOrUnsafeIframeOriginsAndPaths() {
        for (url in listOf("http://playmogo.com/e/id", "https://playmogo.com.evil.example/e/id",
            "https://user:pass@playmogo.com/e/id", "https://playmogo.com:444/e/id",
            "https://playmogo.com/e/id?token=secret", "https://playmogo.com/e/id#secret",
            "https://playmogo.com/other/id", "javascript:alert(1)", "https://127.0.0.1/e/id")) {
            val api = AnimeGatePlaybackApi(post = { target, _ -> if (target.endsWith("/resolve")) resolved(url) else ticket })
            assertThrows(IllegalArgumentException::class.java) { api.resolve("episode", "link") }
        }
    }

    @Test fun acceptsOnlyTheTwoConfirmedPostRoutes() {
        assertTrue(AnimeGatePlaybackApi.isPostUrl("https://api.animegate.pl/api/v1/episodes/id/playback"))
        assertTrue(AnimeGatePlaybackApi.isPostUrl("https://api.animegate.pl/api/v1/episodes/id/playback/resolve"))
        for (url in listOf("https://animegate.pl/api/v1/episodes/id/playback", "https://api.animegate.pl/api/v1/episodes/id/view",
            "https://api.animegate.pl/api/v1/episodes/id/playback?token=secret", "https://api.animegate.pl/api/v1/auth/me")) {
            assertFalse(url, AnimeGatePlaybackApi.isPostUrl(url))
        }
        val api = AnimeGatePlaybackApi(post = { _, _ -> throw AssertionError("Unexpected network request") })
        assertThrows(IllegalArgumentException::class.java) { api.resolve("../auth", "id") }
        assertThrows(IllegalArgumentException::class.java) { api.resolve("id", "id?token=secret") }
    }
}
