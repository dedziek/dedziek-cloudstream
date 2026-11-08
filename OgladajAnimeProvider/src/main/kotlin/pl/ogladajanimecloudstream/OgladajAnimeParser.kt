package pl.ogladajanimecloudstream

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

object OgladajAnimeParser {
    const val BASE = "https://ogladajanime.pl"

    data class Card(
        val title: String,
        val url: String,
        val poster: String?,
        val type: String? = null
    )

    data class Episode(
        val title: String,
        val url: String,
        val episode: Double?,
        val epId: String
    )

    data class Detail(
        val title: String,
        val url: String,
        val poster: String?,
        val plot: String?,
        val year: Int?,
        val type: String?,
        val status: String?,
        val genres: List<String>,
        val episodes: List<Episode>
    )

    fun document(html: String, baseUri: String = BASE): Document = Jsoup.parse(html, baseUri)

    fun isSiteUrl(url: String): Boolean = try {
        val host = URI(url).host?.lowercase() ?: ""
        host == "ogladajanime.pl" || host == "www.ogladajanime.pl" || url.startsWith("/")
    } catch (_: Exception) {
        false
    }

    private fun cleanUrl(raw: String, baseUri: String = BASE): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "${baseUri.trimEnd('/')}$raw"
        else -> "${baseUri.trimEnd('/')}/$raw"
    }

    fun cardsFromHtml(html: String, baseUri: String = BASE): List<Card> {
        if (html.isBlank()) return emptyList()
        val doc = document(html, baseUri)
        return cards(doc, baseUri)
    }

    fun cards(doc: Document, baseUri: String = BASE): List<Card> {
        val elements = doc.select(".anime-item, .card.bg-white, div.col-6.col-sm-4, div.col-12.col-lg-6")
        val results = mutableListOf<Card>()
        val seenUrls = mutableSetOf<String>()

        for (el in elements) {
            val linkEl = el.selectFirst("a[href*='/anime/']") ?: continue
            val href = linkEl.attr("href").trim()
            if (href.isBlank() || href == "#") continue
            val absoluteUrl = cleanUrl(href, baseUri)
            if (!seenUrls.add(absoluteUrl)) continue

            val img = el.selectFirst("img")
            val poster = img?.let {
                val dataSrcset = it.attr("data-srcset").trim()
                val src = it.attr("src").trim()
                when {
                    dataSrcset.isNotEmpty() -> dataSrcset.substringBefore(" ")
                    src.isNotEmpty() -> src
                    else -> null
                }
            }?.let { cleanUrl(it, baseUri) }

            val title = linkEl.attr("aria-label").trim().ifEmpty {
                el.selectFirst(".card-title a, .text-trim-2, h5 a, h4")?.text()?.trim() ?:
                img?.attr("alt")?.trim() ?:
                linkEl.text().trim()
            }
            if (title.isBlank()) continue

            val typeBadge = el.selectFirst(".badge")?.text()?.trim()
            results.add(Card(title = title, url = absoluteUrl, poster = poster, type = typeBadge))
        }

        return results
    }

    fun details(doc: Document, url: String): Detail {
        val title = doc.selectFirst("#anime_name_id, h4.m-0[anime_id], h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.removeSuffix(" - Oglądaj Anime")?.trim()
            ?: "Anime"

        val poster = doc.selectFirst(".col-3 img, img.float-right, .lozad.rounded")?.let {
            val srcset = it.attr("data-srcset").trim()
            val src = it.attr("src").trim()
            when {
                srcset.isNotEmpty() -> srcset.substringBefore(" ")
                src.isNotEmpty() -> src
                else -> null
            }
        }?.let { cleanUrl(it, BASE) }
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { cleanUrl(it, BASE) }

        val plot = doc.selectFirst("#animeDesc, p.text-gray.content")?.text()?.trim()
            ?.ifEmpty { null }
            ?: doc.selectFirst("meta[name='description']")?.attr("content")?.trim()?.ifEmpty { null }

        val bodyText = doc.text()
        val year = Regex("""Start emisji:\s*(\d{4})""").find(bodyText)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(bodyText)?.groupValues?.get(1)?.toIntOrNull()

        val status = Regex("""Status:\s*([^<\n]+)""").find(bodyText)?.groupValues?.get(1)?.trim()

        val type = doc.selectFirst(".outlines span.btn, .badge")?.text()?.trim()

        val genres = doc.select("span.badge[href*='/search/'], a.badge[href*='/search/'], a[href*='/search/custom/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("?") && it.length < 30 }
            .distinct()

        val episodes = parseEpisodes(doc, url)

        return Detail(
            title = title,
            url = url,
            poster = poster,
            plot = plot,
            year = year,
            type = type,
            status = status,
            genres = genres,
            episodes = episodes
        )
    }

    fun parseEpisodes(doc: Document, url: String): List<Episode> {
        val epElements = doc.select("[ep_id]")
        val episodes = mutableListOf<Episode>()
        val seenEpIds = mutableSetOf<String>()

        for (el in epElements) {
            val epId = el.attr("ep_id").trim()
            if (epId.isEmpty() || !seenEpIds.add(epId)) continue

            val valStr = el.attr("value").trim()
            val epNum = valStr.toDoubleOrNull()

            val titleAttr = el.attr("title").trim()
            val innerTitle = el.selectFirst(".text-trim, p")?.text()?.trim()
            val title = when {
                titleAttr.isNotEmpty() -> titleAttr
                !innerTitle.isNullOrEmpty() -> innerTitle
                epNum != null && epNum > 0.0 -> "Odcinek ${if (epNum % 1.0 == 0.0) epNum.toInt() else epNum}"
                else -> "Odcinek $epId"
            }

            val epUrl = if (url.contains("?")) {
                "$url&watchepisode=$epId&subaction=player"
            } else {
                "$url?watchepisode=$epId&subaction=player"
            }

            episodes.add(Episode(title = title, url = epUrl, episode = epNum, epId = epId))
        }

        // If the site had episodes in descending or reverse order, maintain proper playback order
        return if (episodes.size > 1 && (episodes.first().episode ?: 0.0) > (episodes.last().episode ?: 0.0)) {
            episodes.reversed()
        } else {
            episodes
        }
    }
}
