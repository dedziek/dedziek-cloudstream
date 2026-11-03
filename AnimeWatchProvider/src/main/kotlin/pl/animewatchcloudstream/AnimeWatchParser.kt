package pl.animewatchcloudstream

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Parsing only; performs no network requests or script evaluation. */
object AnimeWatchParser {
    const val BASE = "https://animewatch.pl"

    data class Card(val title: String, val url: String, val poster: String?, val type: String?)
    data class EpisodeData(val title: String, val url: String, val episode: Double?)
    data class Details(
        val title: String,
        val poster: String?,
        val plot: String?,
        val year: Int?,
        val genres: List<String>,
        val status: String?,
        val type: String?,
        val episodes: List<EpisodeData>,
        val recommendations: List<Card>,
    )

    private val siteHosts = setOf("animewatch.pl", "www.animewatch.pl")
    private val animePath = Regex("^/anime/[^/]+/?$")
    private val episodeNumber = Regex("""\d+(?:[.,]\d+)?""")
    private val titleNumber = Regex("""(?:Odcinek|Episode|Ep\.?)\s*:?[ ]*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)

    fun document(html: String, url: String): Document = Jsoup.parse(html.removePrefix("\uFEFF"), url)

    fun isSiteUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in siteHosts && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)

    /** Resolve web URLs and canonicalize links to this site, retaining escaped paths and queries. */
    fun absolute(base: String, raw: String): String? = runCatching {
        if (raw.isBlank()) return null
        val uri = URI(base).resolve(raw.trim().replace("\\/", "/").replace(" ", "%20"))
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in listOf("http", "https") || uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) return null
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || !host.contains('.') ||
            host.contains(':') || host.matches(Regex("[0-9.]+"))) return null
        if (host in siteHosts) {
            BASE + uri.rawPath.orEmpty().ifBlank { "/" } +
                (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        } else uri.toASCIIString()
    }.getOrNull()

    private fun siteLink(base: String, raw: String): String? =
        absolute(base, raw)?.takeIf(::isSiteUrl)?.substringBefore('#')

    private fun image(element: Element?, base: String): String? = element?.let { img ->
        sequenceOf("data-src", "data-lazy-src", "data-original", "src").map { img.attr(it) }
            .firstNotNullOfOrNull { absolute(base, it) }
    }

    fun cards(root: Element, base: String): List<Card> = root.select("article.bs, .bsx").mapNotNull { container ->
        val anchor = container.selectFirst("a[href]") ?: return@mapNotNull null
        val url = siteLink(base, anchor.attr("href")) ?: return@mapNotNull null
        if (!animePath.matches(URI(url).path)) return@mapNotNull null
        val img = container.selectFirst("img")
        val title = sequenceOf(
            container.selectFirst("h2[itemprop=headline], h2")?.text(), anchor.attr("title"),
            container.selectFirst(".tt")?.ownText(), img?.attr("alt"),
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return@mapNotNull null
        Card(title, url, image(img, base), container.selectFirst(".typez")?.text()?.takeIf { it.isNotBlank() })
    }.distinctBy { it.url }

    fun details(doc: Document, url: String): Details {
        val title = sequenceOf(
            doc.selectFirst(".infox h1.entry-title, h1.entry-title")?.text(),
            doc.selectFirst("meta[property=og:title]")?.attr("content"),
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "AnimeWatch"
        val metadata = doc.select(".infox .spe > span").mapNotNull { span ->
            val label = span.selectFirst("b")?.text()?.trim()?.removeSuffix(":")?.lowercase() ?: return@mapNotNull null
            label to span.clone().apply { select("b").remove() }.text().trim()
        }
        fun value(vararg labels: String): String? = metadata.firstOrNull { it.first in labels }?.second?.takeIf { it.isNotBlank() }
        val poster = image(doc.selectFirst(".bigcover .thumb img, .thumbook .thumb img, .thumb img"), url)
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { absolute(url, it) }
        val episodes = doc.select(".eplister li a[href]").mapNotNull { anchor ->
            val epUrl = siteLink(url, anchor.attr("href")) ?: return@mapNotNull null
            if (!URI(epUrl).path.matches(Regex("^/[^/]+/?$"))) return@mapNotNull null
            val name = anchor.selectFirst(".epl-title")?.text()?.takeIf { it.isNotBlank() }
                ?: anchor.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val numberText = anchor.selectFirst(".epl-num")?.text().orEmpty().trim()
            val number = if (episodeNumber.matches(numberText)) numberText
                else titleNumber.find(name)?.groupValues?.get(1)
            EpisodeData(name, epUrl, number?.replace(',', '.')?.toDoubleOrNull())
        }.distinctBy { it.url }.sortedBy { it.episode ?: Double.MAX_VALUE }
        val current = siteLink(url, url)
        return Details(
            title, poster,
            doc.selectFirst(".synp .entry-content, .entry-content[itemprop=description]")?.text()?.takeIf { it.isNotBlank() },
            value("wydano", "released")?.let { Regex("(?:19|20)\\d{2}").find(it)?.value?.toIntOrNull() },
            doc.select(".infox .genxed a").map { it.text().trim() }.filter { it.isNotBlank() }.distinct(),
            value("status"), value("typ", "type"), episodes,
            cards(doc.selectFirst("#mainarea, #content") ?: doc, url).filter { it.url != current },
        )
    }

    fun nextPage(doc: Document, current: String): String? =
        doc.select(".hpage a.r[href], .pagination a.next[href], a[rel=next][href]")
            .firstNotNullOfOrNull { siteLink(current, it.attr("href")) }?.takeIf { it != current }

    fun isChallenge(doc: Document): Boolean = doc.title().contains("Just a moment", true) ||
        doc.title().contains("Attention Required", true) || doc.selectFirst("#challenge-form, #cf-challenge-running") != null
}
