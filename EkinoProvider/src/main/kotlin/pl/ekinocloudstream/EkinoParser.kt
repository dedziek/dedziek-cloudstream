package pl.ekinocloudstream

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

/** Parsing only: never executes page scripts or exports session data. */
object EkinoParser {
    // ekino-tv.pl redirects to this official origin (verified 2026-11-04).
    const val BASE = "https://ekino.ws"
    data class Card(val title: String, val url: String, val poster: String?, val year: Int?, val series: Boolean)
    data class EpisodeData(val title: String, val url: String, val season: Int, val episode: Int)
    data class Details(val title: String, val poster: String?, val plot: String?, val year: Int?, val tags: List<String>, val episodes: List<EpisodeData>)
    data class Player(val url: String, val host: String, val language: String)
    data class Embed(val url: String, val direct: Boolean)

    private val episodePattern = Regex("""\+season\[(\d{1,3})]\+episode\[(\d{1,4})]""", RegexOption.IGNORE_CASE)
    private val showPlayer = Regex("""ShowPlayer\(\s*['"]([a-zA-Z0-9_-]{1,32})['"]\s*,\s*['"]([a-zA-Z0-9_-]{1,512})['"]\s*\)""")
    fun document(html: String, url: String = BASE): Document = Jsoup.parse(html, url)

    fun isSiteUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "ekino.ws" && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)

    fun absolute(base: String, raw: String): String? = runCatching {
        // Square brackets are part of the site's ordinary season/page paths.
        val uri = URI(base).resolve(raw.trim().replace("\\/", "/").replace("[", "%5B").replace("]", "%5D"))
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) return null
        if (!host.contains('.') || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.contains(':') || host.matches(Regex("[0-9.]+"))) return null
        uri.toASCIIString()
    }.getOrNull()

    /** Legacy catalogue links may be normalized; saved credentials still only go to BASE. */
    fun siteUrl(base: String, raw: String): String? {
        val url = absolute(base, raw) ?: return null
        val uri = URI(url)
        if (uri.host !in setOf("ekino.ws", "ekino-tv.pl") || uri.scheme != "https") return null
        return BASE + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun isDetailUrl(url: String): Boolean = isSiteUrl(url) && URI(url).path.matches(
        Regex("/(movie/show/[^/]+/\\d+|serie/show/[^/]+|serie/watch/[^/]+)/*"))

    fun isChallenge(doc: Document): Boolean = doc.title().contains("Attention Required", true) ||
        doc.title().contains("Just a moment", true) || doc.selectFirst("#cf-challenge-running, #challenge-form, #cf-error-details") != null

    // The header login form is present on every anonymous catalogue page.
    fun isLoginPage(doc: Document): Boolean = doc.title().trim().startsWith("Logowanie", true) ||
        (doc.selectFirst("h1.title, .movies-list-item, .scope_left, #list-series, .playerContainer") == null &&
            doc.selectFirst("form[action=/login] input[type=password]") != null &&
            doc.selectFirst("#login_fr") == null)

    fun cards(root: Element, base: String = BASE): List<Card> = root.select("a[href]").mapNotNull { anchor ->
        val url = siteUrl(base, anchor.attr("href")) ?: return@mapNotNull null
        if (!isDetailUrl(url) || URI(url).path.startsWith("/serie/watch/")) return@mapNotNull null
        val container = anchor.parents().firstOrNull { it.hasClass("movies-list-item") || it.tagName() == "li" }
            ?: anchor
        val image = container.selectFirst("img") ?: anchor.selectFirst("img")
        val title = sequenceOf(container.selectFirst(".title a")?.text(), anchor.attr("title").removePrefix("Oglądaj: "),
            image?.attr("alt"), anchor.text()).firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: return@mapNotNull null
        val poster = image?.let { img -> sequenceOf("data-src", "src").map { img.attr(it) }.firstOrNull(String::isNotBlank) }
            ?.let { absolute(base, it) }
        val year = Regex("(?:19|20)\\d{2}").find(container.selectFirst(".cates")?.text().orEmpty())?.value?.toIntOrNull()
        Card(title, url, poster, year, URI(url).path.startsWith("/serie/show/"))
    }.groupBy { it.url }.values.map { versions -> versions.first().copy(
        poster = versions.firstNotNullOfOrNull { it.poster }, year = versions.firstNotNullOfOrNull { it.year }) }

    fun nextPage(doc: Document, current: String): String? {
        val path = URI(current).path
        val page = Regex("strona\\[(\\d+)]").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        return doc.select("#pager a[href]").mapNotNull { siteUrl(current, it.attr("href")) }
            .firstOrNull { candidate ->
                val uri = URI(candidate)
                uri.path.substringBefore("strona[") == path.substringBefore("strona[") &&
                    Regex("strona\\[(\\d+)]").find(uri.path)?.groupValues?.get(1)?.toIntOrNull() == page + 1
            }
    }

    fun details(doc: Document, url: String): Details {
        val title = doc.selectFirst("h1.title")?.text()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Brak tytułu materiału")
        val poster = doc.selectFirst("img.moviePoster")?.attr("src")?.let { absolute(url, it) }
        val cates = doc.selectFirst(".catBox .cat, .cates, .category")?.text().orEmpty()
        val year = Regex("(?:19|20)\\d{2}").find(cates)?.value?.toIntOrNull()
        val tags = doc.select(".catBox .cat a[href^=/movie/cat/], .cates a").map { it.text() }.filter(String::isNotBlank).distinct()
        val episodes = doc.select("#list-series a[href]").mapNotNull { anchor ->
            val href = siteUrl(url, anchor.attr("href")) ?: return@mapNotNull null
            if (!isDetailUrl(href) || !URI(href).path.startsWith("/serie/watch/")) return@mapNotNull null
            if (URI(href).path.substringAfter("/serie/watch/").substringBefore("+season[").isBlank()) return@mapNotNull null
            val match = episodePattern.find(URI(href).path) ?: return@mapNotNull null
            EpisodeData(anchor.text().ifBlank { "Odcinek ${match.groupValues[2]}" }, href,
                match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode }))
        return Details(title, poster, doc.selectFirst(".descriptionMovie")?.text()?.takeIf(String::isNotBlank), year, tags, episodes)
    }

    fun playerDelaySeconds(doc: Document): Int? {
        val script = doc.select("script").map { it.data() }.firstOrNull { it.contains("function ShowPlayer(") } ?: return null
        val seconds = Regex("""\btime\s*=\s*(\d{1,3})\s*;""").find(script)?.groupValues?.get(1)?.toIntOrNull()
        return seconds?.takeIf { it in 0..60 }
    }

    fun players(doc: Document, url: String): List<Player> {
        // Construct only the free-player route literally advertised by the page's own ShowPlayer function.
        if (doc.select("script").none { it.data().contains("/watch/f/") } || playerDelaySeconds(doc) == null) return emptyList()
        return doc.select(".playerContainer [onclick]").filter { it.parents().none { parent -> parent.id() == "premium" } }.mapNotNull { anchor ->
            val match = showPlayer.matchEntire(anchor.attr("onclick").trim().removeSuffix(";")) ?: return@mapNotNull null
            val (host, id) = match.destructured
            if (host.lowercase(Locale.ROOT) in setOf("premium", "pr", "pro")) return@mapNotNull null
            val tab = doc.select(".players a[href]").firstOrNull { it.attr("href") == "#$id-$host" }
            val language = tab?.selectFirst("i[title]")?.attr("title").orEmpty()
            Player("${URI(url).scheme}://${URI(url).authority}/watch/f/$host/$id", host, language)
        }.filter { isSiteUrl(it.url) }.distinctBy { it.url }
    }

    fun embeds(doc: Document, base: String): List<Embed> = doc.select("iframe[src], a.buttonprch[href], video[src], video source[src]")
        .filter { it.parents().none { parent -> parent.id() == "premium" } }
        .mapNotNull { element ->
            val raw = if (element.tagName() == "a") element.attr("href") else element.attr("src")
            val target = absolute(base, raw) ?: return@mapNotNull null
            if (raw.isBlank()) return@mapNotNull null
            if (isSiteUrl(target) && !URI(target).path.startsWith("/watch/f/")) return@mapNotNull null
            Embed(target, element.tagName() in setOf("video", "source"))
        }.distinctBy { it.url }
}
