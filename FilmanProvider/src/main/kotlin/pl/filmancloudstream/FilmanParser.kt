package pl.filmancloudstream

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Pure parsing: no network, session data, Android APIs or JavaScript execution. */
object FilmanParser {
    const val BASE = "https://filman.cc"
    data class Card(val title: String, val url: String, val poster: String?, val year: Int?, val series: Boolean)
    data class EpisodeData(val title: String, val url: String, val season: Int?, val episode: Int?)
    data class Details(
        val title: String,
        val poster: String?,
        val plot: String?,
        val year: Int?,
        val episodes: List<EpisodeData>,
        val tags: List<String> = emptyList(),
        val backdrop: String? = null
    )
    data class Player(val url: String, val label: String, val version: String = "", val quality: String = "") {
        fun displayName(extractorName: String): String = listOf(
            extractorName.ifBlank { label.substringBefore(" · ") }, version, quality
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }
    private val episodePattern = Regex("""\[?s(\d+)\s*e(\d+)\]?""", RegexOption.IGNORE_CASE)

    fun isSiteUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "filman.cc" && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)

    fun absolute(base: String, raw: String): String? = runCatching {
        val uri = URI(base).resolve(raw.trim().replace("\\/", "/"))
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) return null
        // Video URLs come from untrusted HTML. Do not hand local-network URLs to extractors.
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || !host.contains('.') ||
            host.contains(':') || host.matches(Regex("[0-9.]+"))) return null
        uri.toASCIIString()
    }.getOrNull()

    fun document(html: String, url: String): Document = Jsoup.parse(html, url)

    fun isLoginPage(doc: Document): Boolean = doc.selectFirst("form#signin-form") != null ||
        doc.title().trim().startsWith("Logowanie", true)

    fun isChallenge(doc: Document): Boolean = doc.title().contains("Attention Required", true) ||
        doc.title().contains("Just a moment", true) || doc.selectFirst("#cf-challenge-running, #challenge-form") != null

    fun cards(root: Element, base: String): List<Card> {
        // Normal catalog and AJAX /szukam results have different card layouts.
        return root.select("a[href]").mapNotNull { anchor ->
            val url = absolute(base, anchor.attr("href")) ?: return@mapNotNull null
            if (!isSiteUrl(url) || !URI(url).path.matches(Regex("^/[ms]/[^/]+.*"))) return@mapNotNull null
            val container = anchor.parents().firstOrNull {
                it.selectFirst(".film_title") != null && it.select(".poster").size <= 1
            } ?: anchor
            val image = anchor.selectFirst("img") ?: container.selectFirst(".poster img")
            val title = sequenceOf(
                container.selectFirst(".film_title")?.text(), anchor.selectFirst(".title")?.text(),
                anchor.attr("title"), image?.attr("alt"), anchor.text()
            ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return@mapNotNull null
            val poster = image?.let { img ->
                sequenceOf("data-src", "data-original", "src").map { img.attr(it) }
                    .firstOrNull { it.isNotBlank() }?.let { absolute(base, it) }
            }
            val year = Regex("(?:19|20)\\d{2}").find(container.selectFirst(".film_year, .year")?.text().orEmpty())?.value?.toIntOrNull()
            Card(title, url, poster, year, URI(url).path.startsWith("/s/"))
        }.distinctBy { it.url }
    }

    fun details(doc: Document, url: String): Details {
        val title = sequenceOf(
            doc.selectFirst("#single-info h1[itemprop=name]")?.ownText(),
            doc.selectFirst("[itemprop=title]")?.text(), doc.selectFirst("h1")?.ownText(),
            doc.selectFirst("meta[property=og:title]")?.attr("content")
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Filman"
        val poster = sequenceOf(
            doc.selectFirst("#single-poster img")?.attr("src"),
            doc.selectFirst("meta[property=og:image]")?.attr("content")
        ).firstOrNull { !it.isNullOrBlank() }?.let { absolute(url, it) }
        val backdrop = sequenceOf(
            doc.selectFirst("#item-headline, .item-headline, #backdrop, .backdrop")?.attr("style")?.let { style ->
                Regex("""url\(['"]?(.*?)['"]?\)""").find(style)?.groupValues?.get(1)
            },
            doc.selectFirst("meta[property=og:image]")?.attr("content")
        ).firstOrNull { !it.isNullOrBlank() }?.let { absolute(url, it) }
        val yearText = doc.select("#single-info .flm-meta-value, .info li, [itemprop=datePublished]").text()
        val tags = doc.select("[itemprop=genre], .genres a, .genre a, a[href^='/gatunek/']").map { it.text().trim() }
            .filter { it.isNotBlank() }.distinct()
        val episodes = doc.select("#episode-list a[href], a[href]").mapNotNull { anchor ->
            val match = episodePattern.find(anchor.text()) ?: return@mapNotNull null
            val epUrl = absolute(url, anchor.attr("href"))?.takeIf(::isSiteUrl) ?: return@mapNotNull null
            EpisodeData(anchor.text().replace(episodePattern, "").trim().ifBlank { anchor.text() }, epUrl,
                match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
        return Details(title, poster, doc.selectFirst(".description, [itemprop=description]")?.text(),
            Regex("(?:19|20)\\d{2}").find(yearText)?.value?.toIntOrNull(), episodes, tags, backdrop)
    }

    fun nextPage(doc: Document, current: String): String? =
        doc.select("a[rel=next], .pagination .next a, .pagination a").firstOrNull {
            it.attr("rel") == "next" || it.parent()?.hasClass("next") == true ||
                it.text().trim() in setOf("Następna", "Następna »", "»", ">", "›")
        }?.attr("href")?.let { absolute(current, it) }?.takeIf(::isSiteUrl)

    fun routeToken(html: String): String? =
        Regex("""(?:var|let|const)\s+(?:rt|routeToken)\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)

    fun csrf(doc: Document): String? = sequenceOf(
        doc.selectFirst("input[name=_csrf]")?.attr("value"),
        doc.selectFirst("meta[name=csrf-token]")?.attr("content")
    ).firstOrNull { !it.isNullOrBlank() }

    fun players(doc: Document, url: String): List<Player> = doc.select("#links tr").mapNotNull { row ->
        val cells = row.select("td")
        if (cells.size < 3) return@mapNotNull null
        // Timed "free premium" previews are deliberately not promoted to unrestricted streams.
        if (row.selectFirst("[data-mp4]") != null) return@mapNotNull null
        val encoded = row.selectFirst("[data-iframe]")?.attr("data-iframe") ?: row.attr("data-iframe")
        val idElement = row.selectFirst("[data-link-id], [data-id]") ?: row
        val id = sequenceOf(idElement.attr("data-link-id"), idElement.attr("data-id"))
            .firstOrNull { it.matches(Regex("\\d+")) }
        val target = when {
            encoded.isNotBlank() -> decodeBase64(encoded)?.let { embeddedUrl(it, url) }
            id != null -> "$BASE/link/go/$id"
            else -> null
        } ?: return@mapNotNull null
        val version = cells[1].text().ifBlank {
            row.classNames().firstOrNull { it.startsWith("version-") }?.removePrefix("version-").orEmpty()
        }
        val quality = cells[2].text()
        val label = listOf(cells[0].selectFirst("img")?.attr("alt").orEmpty(), version, quality)
            .filter { it.isNotBlank() }.joinToString(" · ")
        Player(target, label, version, quality)
    }.distinctBy { Triple(it.url, it.version, it.quality) }

    fun embeddedUrl(payload: String, base: String): String? {
        val clean = payload.trim().replace("\\/", "/")
        if (clean.startsWith("https://") || clean.startsWith("http://") || clean.startsWith("//")) return absolute(base, clean)
        val iframe = Jsoup.parse(clean).selectFirst("iframe[src], video[src], source[src]")?.attr("src")
        val jsonSrc = Regex("""["'](?:src|url)["']\s*:\s*["']([^"']+)["']""").find(clean)?.groupValues?.get(1)
        return (iframe ?: jsonSrc)?.let { absolute(base, it) }
    }

    /** Decodes ordinary host-wrapper obfuscation, not encrypted/DRM video. Never executes page JS. */
    fun unwrapHost(html: String, base: String): String? {
        fun variable(name: String) = Regex("""(?:var|let|const)\s+$name\s*=\s*['"]([^'"]*)['"]""").find(html)?.groupValues?.get(1)
        val encoded = variable("_e") ?: return null
        val raw = decodeBase64Bytes(encoded) ?: return null
        val pieces = listOf(variable("_a"), variable("_b"), variable("_c"))
        val result = if (pieces.all { it != null } && pieces.joinToString("").isNotEmpty()) {
            val key = pieces.joinToString("").toByteArray(Charsets.UTF_8)
            String(ByteArray(raw.size) { (raw[it].toInt() xor key[it % key.size].toInt()).toByte() }, Charsets.UTF_8)
        } else {
            String(raw, Charsets.UTF_8).map { c -> when(c) {
                in 'a'..'z' -> ('a'.code + (c.code - 'a'.code + 13) % 26).toChar()
                in 'A'..'Z' -> ('A'.code + (c.code - 'A'.code + 13) % 26).toChar()
                else -> c
            } }.joinToString("")
        }
        return absolute(base, result)
    }

    fun decodeBase64(text: String): String? = decodeBase64Bytes(text)?.toString(Charsets.UTF_8)

    // API23-compatible and independently testable on a JVM without Android's Base64 stub.
    private fun decodeBase64Bytes(text: String): ByteArray? {
        val input = text.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
        if (input.isEmpty() || input.length > 1_000_000 || !input.matches(Regex("[A-Za-z0-9+/]*={0,2}")) ||
            input.trimEnd('=').length % 4 == 1 || (input.contains('=') && input.length % 4 != 0)) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0; var bits = 0
        for (char in input.trimEnd('=')) {
            buffer = (buffer shl 6) or alphabet.indexOf(char)
            bits += 6
            if (bits >= 8) { bits -= 8; output.write((buffer shr bits) and 255) }
        }
        return output.toByteArray()
    }
}
