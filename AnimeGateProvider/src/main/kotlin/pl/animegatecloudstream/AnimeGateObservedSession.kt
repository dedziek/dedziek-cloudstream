package pl.animegatecloudstream

/** Holds one exact observed endpoint, not raw navigation history or request headers. */
internal class AnimeGateObservedSession {
    private var selected: String? = null

    @Synchronized fun observe(method: String, url: String) {
        if (method != "GET") return
        val candidate = AnimeGateAuthPolicy.observedSessionUrl(url) ?: return
        if (selected?.let(AnimeGateAuthPolicy::isApi) != true) selected = candidate
    }

    @Synchronized fun url(): String? = selected
}
