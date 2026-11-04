package pl.animewatchcloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeWatchPlaybackSessionTest {
    private val request = AnimeWatchPlaybackSession.Request("https://animewatch.pl/episode-1/", "https://host.example/e/code", "https://frame.example/id/code")
    private val stream = AnimeWatchPlayback.Stream("https://cdn.example/master.m3u8?token=verified", mapOf("User-Agent" to "WebView"), "https://frame.example/id/code", true)

    @Test fun remembersTheLastEpisodeNeedingUserVerification() {
        val session = AnimeWatchPlaybackSession()
        assertNull(session.pending())
        session.remember(request)
        assertEquals(request, session.pending())
        val next = request.copy(episodeUrl = "https://animewatch.pl/episode-2/")
        session.remember(next)
        assertEquals(next, session.pending())
    }

    @Test fun returnsTheVerifiedStreamOnlyForTheSameEpisodeAndOnlyOnce() {
        val session = AnimeWatchPlaybackSession { 0L }
        session.store(request, stream)
        assertNull(session.take("https://animewatch.pl/episode-2/"))
        assertEquals(stream, session.take(request.episodeUrl))
        assertNull(session.take(request.episodeUrl))
    }

    @Test fun expiresUnusedSignedMediaInsteadOfReusingAnOldToken() {
        var now = 1_000L
        val session = AnimeWatchPlaybackSession { now }
        session.remember(request)
        session.store(request, stream)
        now += 60_000L
        assertNull(session.take(request.episodeUrl))
        assertEquals(request, session.pending())
    }
}
