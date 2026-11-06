package pl.dedziek.ekinotv

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Locale

/** URLs observed on the official site; no speculative mirror or hostname replacement. */
internal object Site {
    const val ENTRY = "https://ekino-tv.pl"
    private val hosts = setOf("ekino-tv.pl", "ekino.ws")

    fun absolute(base: String, raw: String): String? = runCatching {
        val u = URI(base).resolve(raw.trim().replace("\\/", "/").replace("[", "%5B").replace("]", "%5D"))
        val host = u.host?.lowercase(Locale.ROOT) ?: return null
        if (u.scheme != "https" || u.userInfo != null || u.port !in listOf(-1, 443) ||
            !host.contains('.') || host.endsWith(".local") || host.endsWith(".localhost") ||
            host.matches(Regex("[0-9.]+")) || ':' in host) return null
        u.toASCIIString()
    }.getOrNull()

    fun official(url: String) = runCatching {
        absolute(url, url) != null && URI(url).host in hosts
    }.getOrDefault(false)

    fun bridge(url: String) = runCatching {
        absolute(url, url) != null && URI(url).host == "play.ekino.link"
    }.getOrDefault(false)

    fun origin(url: String): String = URI(url).let { "${it.scheme}://${it.rawAuthority}" }
    fun path(url: String): String = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
    fun material(url: String) = official(url) && path(url).matches(
        Regex("/(movie/show/[^/]+/\\d+|serie/show/[^/]+|serie/watch/[^/]+)/*"))

    fun publicPage(url: String): String? = if (official(url) || bridge(url))
        runCatching { origin(url) + URI(url).rawPath.orEmpty() }.getOrNull() else null

    fun wrapper(url: String) = official(url) && path(url).startsWith("/watch/f/")
    fun challengeHost(url: String) = runCatching {
        URI(url).scheme == "https" && URI(url).host == "challenges.cloudflare.com" && URI(url).userInfo == null
    }.getOrDefault(false)
}

internal class SiteProblem(message: String) : Exception(message)

internal data class WebPage(val url: String, val html: String, val active: Boolean = true) {
    val doc: Document by lazy { Jsoup.parse(html, url) }
}

internal data class BrowserSnapshot(val page: WebPage, val frames: List<WebPage> = emptyList(),
                                    val readyState: String = "complete", val documentId: Double = 0.0,
                                    val hasShowPlayer: Boolean = false) {
    val parsed get() = readyState in setOf("interactive", "complete") && page.html.isNotBlank()
    fun gate(): String? = (listOf(page) + frames.filter { it.active }).firstNotNullOfOrNull { p ->
        val d = p.doc
        when {
            d.title().contains("Just a moment", true) || d.title().contains("Attention Required", true) ||
                d.selectFirst("#challenge-running, #cf-challenge-running, #challenge-form, #cf-error-details") != null -> "Cloudflare"
            (Site.wrapper(p.url) || Site.bridge(p.url)) &&
                (d.selectFirst(".cf-turnstile, .g-recaptcha, [data-sitekey]") != null ||
                    d.select("script").any { it.data().contains("turnstile.render") }) -> "CAPTCHA odtwarzacza"
            else -> null
        }
    }

    fun verified(playerRequired: Boolean): Boolean {
        if (!parsed || gate() != null) return false
        val pages = listOf(page) + frames.filter { it.active }
        return if (playerRequired) pages.any { p ->
            (Site.wrapper(p.url) || Site.bridge(p.url)) && Catalogue.embeds(p).isNotEmpty()
        } else Site.official(page.url) && page.doc.selectFirst(
            ".movies-list-item, #list-series, h1.title, .playerContainer, form[action='/search/qf']") != null
    }
}

internal data class Card(val title: String, val url: String, val poster: String?, val year: Int?, val series: Boolean)
internal data class Episode(val name: String, val url: String, val season: Int, val number: Int)
internal data class Material(val title: String, val poster: String?, val plot: String?, val year: Int?,
                             val tags: List<String>, val episodes: List<Episode>)
internal data class Player(val host: String, val id: String, val language: String) {
    val identity get() = "$host/$id"
    val languageKey get() = when (val key = language.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")) {
        "lektor", "lektor polski" -> "lektor pl"
        "napisy", "napisy polskie" -> "napisy pl"
        "dubbing", "dubbing polski" -> "dubbing pl"
        else -> key
    }
}

/** Metadata and publicly advertised free-player controls; scripts execute only in WebView. */
internal object Catalogue {
    private val invocation = Regex("""ShowPlayer\(\s*['"]([A-Za-z0-9_-]{1,32})['"]\s*,\s*['"]([A-Za-z0-9_-]{1,512})['"]\s*\)\s*;?""")
    private val episode = Regex("""\+season\[(\d{1,3})]\+episode\[(\d{1,4})]""")
    private val year = Regex("(?:19|20)\\d{2}")

    fun cards(page: WebPage): List<Card> {
        val cards = linkedMapOf<String, Card>()
        for (a in page.doc.select("a[href]")) {
            val url = Site.absolute(page.url, a.attr("href"))?.takeIf(Site::material) ?: continue
            if (Site.path(url).startsWith("/serie/watch/")) continue
            val row = a.parents().firstOrNull { it.hasClass("movies-list-item") || it.tagName() == "li" } ?: a
            val image = row.selectFirst("img")
            val title = sequenceOf(row.selectFirst(".title a")?.text(), a.attr("title").removePrefix("Oglądaj: "),
                image?.attr("alt"), a.text()).firstOrNull { !it.isNullOrBlank() }?.trim() ?: continue
            val poster = image?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.let { Site.absolute(page.url, it) }
            val item = Card(title, url, poster, year.find(row.selectFirst(".cates")?.text().orEmpty())?.value?.toIntOrNull(),
                Site.path(url).startsWith("/serie/show/"))
            val previous = cards[url]
            cards[url] = if (previous == null) item else previous.copy(
                poster = previous.poster ?: item.poster, year = previous.year ?: item.year)
        }
        return cards.values.toList()
    }

    fun hasNext(page: WebPage): Boolean {
        val current = Regex("strona\\[(\\d+)]").find(Site.path(page.url))?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val prefix = Site.path(page.url).substringBefore("strona[")
        return page.doc.select("#pager a[href]").any { a ->
            val url = Site.absolute(page.url, a.attr("href")) ?: return@any false
            Site.official(url) && Site.path(url).substringBefore("strona[") == prefix &&
                Regex("strona\\[(\\d+)]").find(Site.path(url))?.groupValues?.get(1)?.toIntOrNull() == current + 1
        }
    }

    fun material(page: WebPage): Material {
        require(Site.material(page.url))
        val title = requireNotNull(page.doc.selectFirst("h1.title")?.text()?.takeIf(String::isNotBlank)) { "Brak tytułu materiału" }
        val episodes = page.doc.select("#list-series a[href]").mapNotNull { a ->
            val url = Site.absolute(page.url, a.attr("href"))?.takeIf(Site::material) ?: return@mapNotNull null
            if (!Site.path(url).startsWith("/serie/watch/") ||
                Site.path(url).substringAfter("/serie/watch/").substringBefore("+season[").isBlank()) return@mapNotNull null
            val match = episode.find(Site.path(url)) ?: return@mapNotNull null
            Episode(a.text().ifBlank { "Odcinek ${match.groupValues[2]}" }, url,
                match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.distinctBy { it.season to it.number }.sortedWith(compareBy({ it.season }, { it.number }))
        return Material(title, page.doc.selectFirst("img.moviePoster")?.attr("src")?.let { Site.absolute(page.url, it) },
            page.doc.selectFirst(".descriptionMovie")?.text(),
            year.find(page.doc.selectFirst(".catBox .cat, .cates, .category")?.text().orEmpty())?.value?.toIntOrNull(),
            page.doc.select(".catBox .cat a[href^='/movie/cat/'], .cates a").map { it.text() }.filter(String::isNotBlank).distinct(), episodes)
    }

    fun players(page: WebPage): List<Player> = page.doc.select(".playerContainer [onclick]").mapNotNull { a ->
        if (a.parents().any { it.id() == "premium" }) return@mapNotNull null
        val match = invocation.matchEntire(a.attr("onclick").trim()) ?: return@mapNotNull null
        val host = match.groupValues[1]; val id = match.groupValues[2]
        if (host.lowercase(Locale.ROOT) in setOf("premium", "pr", "pro")) return@mapNotNull null
        val tab = page.doc.select(".players a[href]").firstOrNull { it.attr("href") == "#$id-$host" }
        val icon = tab?.selectFirst("i[title], i[original-title], i[data-original-title]")
        val language = sequenceOf("title", "original-title", "data-original-title")
            .map { icon?.attr(it).orEmpty() }.firstOrNull(String::isNotBlank).orEmpty()
        Player(host, id, language)
    }.distinctBy { it.identity }

    fun embeds(page: WebPage): List<String> = page.doc.select(
        "iframe[src], a.buttonprch[href], video[src], video source[src]" + if (Site.wrapper(page.url)) ", a[target][href]" else "")
        .filter { e -> e.parents().none { it.id() == "premium" } }
        .mapNotNull { e -> Site.absolute(page.url, if (e.tagName() == "a") e.attr("href") else e.attr("src")) }
        .filter { !Site.official(it) && !Site.challengeHost(it) }.distinct()
}
