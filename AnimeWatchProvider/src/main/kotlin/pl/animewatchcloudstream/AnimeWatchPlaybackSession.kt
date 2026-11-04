package pl.animewatchcloudstream

/** The last verification request and one media result, kept in memory for at most a minute. */
class AnimeWatchPlaybackSession(private val now: () -> Long = { System.nanoTime() / 1_000_000L }) {
    data class Request(val episodeUrl: String, val embedUrl: String, val frameUrl: String?)
    private var request: Request? = null
    private var prepared: Pair<String, AnimeWatchPlayback.Stream>? = null
    private var expiresAt = 0L

    @Synchronized fun remember(value: Request) { request = value }
    @Synchronized fun pending(): Request? = request

    @Synchronized fun store(value: Request, stream: AnimeWatchPlayback.Stream) {
        prepared = value.episodeUrl to stream
        expiresAt = now() + 60_000L
    }

    @Synchronized fun take(episodeUrl: String): AnimeWatchPlayback.Stream? {
        val value = prepared ?: return null
        if (now() >= expiresAt) { prepared = null; return null }
        if (value.first != episodeUrl) return null
        prepared = null
        return value.second
    }
}
