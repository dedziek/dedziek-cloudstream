package pl.animegatecloudstream

/** Holds one exact observed endpoint, not raw navigation history or request headers. */
internal class AnimeGateObservedSession {
    private var selected: String? = null

    @Synchronized fun observe(method: String, url: String) {
        if (method != "GET") return
        val candidate = AnimeGateAuthPolicy.observedSessionUrl(url) ?: return
        fun priority(value: String?): Int = when {
            value == null -> -1
            AnimeGateAuthPolicy.isCurrentUserProbeUrl(value) -> 2
            AnimeGateAuthPolicy.isApi(value) -> 1
            else -> 0
        }
        if (priority(candidate) >= priority(selected)) selected = candidate
    }

    @Synchronized fun url(): String? = selected
}
