package pl.animegatecloudstream

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Only redacted fields are retained. Raw URLs and OAuth values never become report state. */
internal class AnimeGateRedirectDiagnostics {
    enum class Source { MAIN_NAVIGATION, LEGACY_NAVIGATION, MAIN_REQUEST }
    data class Block(val reason: AnimeGateAuthPolicy.BlockReason, val source: Source,
                     val scheme: String, val host: String, val path: String, val callbackHost: String)
    private var first: Block? = null
    private var last: Block? = null
    private var count = 0

    @Synchronized fun record(url: String, source: Source): Block? {
        val reason = AnimeGateAuthPolicy.mainBlockReason(url) ?: return null
        val uri = runCatching { URI(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)?.takeIf { it in setOf("https", "http", "about", "intent", "discord") } ?: "OTHER"
        val block = Block(reason, source, scheme, host(uri), path(uri), callbackHost(url, uri))
        if (first == null) first = block
        last = block
        count = minOf(count + 1, 100_000)
        return block
    }

    @Synchronized fun report(): String = buildString {
        appendLine("blocked_count=$count")
        fun appendBlock(prefix: String, block: Block?) {
            if (block == null) return
            appendLine("${prefix}_reason=${block.reason.name}")
            appendLine("${prefix}_source=${block.source.name}")
            appendLine("${prefix}_scheme=${block.scheme}")
            appendLine("${prefix}_host=${block.host}")
            appendLine("${prefix}_path=${block.path}")
            appendLine("${prefix}_callback_host=${block.callbackHost}")
        }
        appendBlock("first_block", first)
        if (last != first) appendBlock("last_block", last)
    }

    private fun host(uri: URI?): String = uri?.host?.lowercase(Locale.ROOT)?.takeIf {
        it.length <= 253 && it.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))
    } ?: "NONE"

    private fun path(uri: URI?): String {
        val vocabulary = setOf("auth", "api", "oauth2", "authorize", "login", "signin", "callback", "discord", "session", "v10")
        return uri?.path?.split('/')?.filter(String::isNotBlank)?.take(6)?.joinToString("/", prefix = "/") {
            if (it in vocabulary) it else "{param}"
        } ?: "NONE"
    }

    private fun callbackHost(url: String, uri: URI?): String {
        if (!AnimeGateAuthPolicy.isDiscord(url)) return "NONE"
        return runCatching {
            val callbacks = uri?.rawQuery.orEmpty().split('&').filter {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") == "redirect_uri"
            }
            if (callbacks.size != 1) "NONE"
            else host(URI(URLDecoder.decode(callbacks.single().substringAfter('='), "UTF-8")))
        }.getOrDefault("NONE")
    }
}
