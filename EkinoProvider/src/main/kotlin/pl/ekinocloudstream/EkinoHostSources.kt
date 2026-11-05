package pl.ekinocloudstream

import java.net.URI
import java.util.Locale

/** Static source metadata only. Never executes JavaScript or clicks a player. */
internal object EkinoHostSources {
    data class Media(val url: String, val hls: Boolean, val referer: String, val headers: Map<String, String> = emptyMap())
    data class Parsed(val media: List<Media>, val frames: List<String>)

    fun publicHttps(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            host.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}")) &&
            listOf(".local", ".localhost", ".internal", ".lan", ".home", ".test", ".invalid").none(host::endsWith) &&
            !EkinoParser.isSiteUrl(url) && !EkinoParser.isBridgeUrl(url)
    }.getOrDefault(false)

    private fun decoded(value: String): String = value.replace("\\/", "/")
        .replace(Regex("""\\u([0-9a-fA-F]{4})|\\x([0-9a-fA-F]{2})""")) { m ->
            (m.groups[1]?.value ?: m.groups[2]!!.value).toInt(16).toChar().toString()
        }

    fun media(raw: String, base: String, declaredType: String = ""): Media? {
        val url = EkinoParser.absolute(base, decoded(raw))?.takeIf(::publicHttps) ?: return null
        val path = URI(url).path.lowercase(Locale.ROOT)
        val type = declaredType.lowercase(Locale.ROOT)
        val hls = path.endsWith(".m3u8") || type in setOf("hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl")
        if (!hls && !path.endsWith(".mp4") && type !in setOf("mp4", "video/mp4")) return null
        return Media(url, hls, base)
    }

    fun parse(html: String, base: String, unpackedScript: String = ""): Parsed {
        val doc = EkinoParser.document(html.take(1_048_576), base)
        if (EkinoParser.isChallenge(doc) || EkinoParser.isPlayerVerification(doc)) return Parsed(emptyList(), emptyList())
        val found = mutableListOf<Media>()
        for (element in doc.select("video[src], video source[src], source[src]")) {
            media(element.attr("src"), base, element.attr("type"))?.let(found::add)
        }
        val scripts = listOf(unpackedScript.take(1_048_576)) + doc.select("script").map { it.data() }
        val property = Regex("""(?:["']?(?:file|src)["']?)\s*:\s*(["'])([^"'\r\n]+)\1""")
        val type = Regex("""["']?type["']?\s*:\s*["']([^"']+)["']""")
        for (script in scripts.take(40)) {
            for (match in property.findAll(script).take(24)) {
                // Read type only within the same simple source object, never from an adjacent source.
                val begin = script.lastIndexOf('{', match.range.first).takeIf { it >= 0 } ?: match.range.first
                val end = script.indexOf('}', match.range.last).takeIf { it >= 0 } ?: match.range.last + 1
                val declared = if (end - begin <= 2048) type.find(script.substring(begin, end))?.groupValues?.get(1).orEmpty() else ""
                media(match.groupValues[2], base, declared)?.let(found::add)
            }
        }
        val frames = doc.select("iframe[src], iframe[data-src]").mapNotNull { frame ->
            val raw = frame.attr("src").takeIf { it.isNotBlank() && it != "about:blank" } ?: frame.attr("data-src")
            EkinoParser.absolute(base, raw)?.takeIf { raw.isNotBlank() && publicHttps(it) && !EkinoLoginPolicy.isVerificationFrame(it) }
        }.distinct().take(4)
        return Parsed(found.distinctBy { it.url }.take(12), frames)
    }
}
