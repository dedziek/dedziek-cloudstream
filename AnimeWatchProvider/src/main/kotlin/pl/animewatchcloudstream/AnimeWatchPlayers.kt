package pl.animewatchcloudstream

import com.lagradost.cloudstream3.extractors.ByseSX
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/** Public iframe and mirror markup only; scripts and advertisement frames are ignored. */
object AnimeWatchPlayers {
    private val byseHosts = setOf("bysedikamoum.com", "byse.sx", "bysebuho.com", "byseqekaho.com", "bysevepoin.com", "bysezejataos.com")

    fun isByse(url: String): Boolean = runCatching { URI(url).host?.lowercase() in byseHosts }.getOrDefault(false)

    fun targets(doc: Document, base: String): List<String> = buildList {
        doc.select("#pembed iframe, #embed_holder .player-embed iframe").forEach { iframe ->
            sequenceOf(iframe.attr("src"), iframe.attr("data-src")).mapNotNull { target(base, it) }.forEach(::add)
        }
        doc.select("select.mirror option[value]").forEach { option ->
            val value = option.attr("value").trim()
            if (value.isBlank()) return@forEach
            val html = decode(value) ?: return@forEach
            Jsoup.parseBodyFragment(html).select("iframe[src], iframe[data-src]").forEach { iframe ->
                sequenceOf(iframe.attr("src"), iframe.attr("data-src")).mapNotNull { target(base, it) }.forEach(::add)
            }
        }
    }.distinct()

    fun target(base: String, raw: String): String? = runCatching {
        if (raw.isBlank()) return null
        val uri = URI(base).resolve(raw.trim())
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.userInfo != null || uri.port !in setOf(-1, 80, 443) ||
            !host.contains('.') || host.endsWith(".local") || host.endsWith(".localhost") || host == "localhost" ||
            host.contains(':') || host.matches(Regex("[0-9.]+")) || host == "animewatch.pl" || host == "www.animewatch.pl") return null
        // Byse's extractor treats the final path component as the video code. The site's
        // optional descriptive slug must therefore be removed from /e/CODE/title URLs.
        if (host in byseHosts) {
            val parts = uri.path.trim('/').split('/')
            if (parts.size >= 2 && parts[0] == "e" && parts[1].matches(Regex("[A-Za-z0-9]+"))) {
                return "${uri.scheme}://$host/e/${parts[1]}"
            }
        }
        uri.toASCIIString()
    }.getOrNull()

    private fun decode(text: String): String? {
        // Android API 23 compatible; java.util.Base64 is only available from API 26.
        val input = text.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
        if (input.length > 100_000 || !input.matches(Regex("[A-Za-z0-9+/]*={0,2}")) ||
            input.trimEnd('=').length % 4 == 1 || (input.contains('=') && input.length % 4 != 0)) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in input.trimEnd('=')) {
            buffer = (buffer shl 6) or alphabet.indexOf(char)
            bits += 6
            if (bits >= 8) { bits -= 8; output.write((buffer shr bits) and 255) }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }
}

/** Alias for the host currently embedded by AnimeWatch. Playback logic remains upstream. */
class AnimeWatchByse : ByseSX() {
    override val name = "Byse (AnimeWatch)"
    override val mainUrl = "https://bysedikamoum.com"
}
