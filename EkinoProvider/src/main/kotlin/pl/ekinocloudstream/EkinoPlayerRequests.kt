package pl.ekinocloudstream

import java.util.Locale

/** Keep language alternatives, but stop fetching mirrors of a language already resolved. */
internal object EkinoPlayerRequests {
    suspend fun visit(players: List<EkinoParser.Player>, load: suspend (EkinoParser.Player) -> Boolean) {
        val resolved = mutableSetOf<String>()
        for (player in players) {
            val language = player.language.trim().lowercase(Locale.ROOT)
            if (language.isNotEmpty() && language in resolved) continue
            if (load(player) && language.isNotEmpty()) resolved.add(language)
        }
    }
}
