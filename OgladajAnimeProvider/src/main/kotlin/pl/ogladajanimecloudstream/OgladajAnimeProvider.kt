package pl.ogladajanimecloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.util.concurrent.CancellationException

class OgladajAnimeProvider : MainAPI() {
    override var mainUrl = OgladajAnimeParser.BASE
    override var name = "OglądajAnime"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "recent" to "Ostatnio dodane",
        "popular" to "Najczęściej wyświetlane",
        "top" to "Najlepsze"
    )

    private data class AjaxResponse(
        @param:JsonProperty("data") val data: String? = null,
        @param:JsonProperty("title") val title: String? = null
    )

    private data class PlayerItem(
        @param:JsonProperty("id") val id: Long? = null,
        @param:JsonProperty("url") val url: String? = null,
        @param:JsonProperty("audio") val audio: String? = null,
        @param:JsonProperty("sub") val sub: String? = null,
        @param:JsonProperty("quality") val quality: String? = null
    )

    private data class PlayerListPayload(
        @param:JsonProperty("url") val url: String? = null,
        @param:JsonProperty("players") val players: List<PlayerItem>? = null
    )

    private val site = OgladajAnimeSite { url, form, headers ->
        if (form == null) app.get(url, headers = headers, allowRedirects = false, cacheTime = 0).okhttpResponse
        else app.post(url, data = form, headers = headers, allowRedirects = false, cacheTime = 0).okhttpResponse
    }

    private suspend fun siteRequest(url: String, form: Map<String, String>? = null): OgladajAnimeSite.Page = try {
        site.request(url, form)
    } catch (error: OgladajAnimeSite.Problem) {
        throw ErrorLoadingException(error.message.orEmpty())
    }

    private fun contentType(type: String?): TvType = when (type?.lowercase()) {
        "movie", "film" -> TvType.AnimeMovie
        "ova", "ona", "special" -> TvType.OVA
        else -> TvType.Anime
    }

    private fun OgladajAnimeParser.Card.toResult(): SearchResponse =
        newAnimeSearchResponse(title, url, contentType(type)) {
            posterUrl = poster
            addDubStatus(DubStatus.Subbed)
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sortTerm = when (request.data) {
            "top" -> "rate"
            "popular" -> "pop"
            else -> "date"
        }

        // On first page of "recent", the home page HTML contains freshly added series
        if (page == 1 && request.data == "recent") {
            val home = siteRequest(mainUrl)
            val cards = OgladajAnimeParser.cards(OgladajAnimeParser.document(home.text, home.url), home.url)
            if (cards.isNotEmpty()) {
                return newHomePageResponse(request.name, cards.map { it.toResult() }, hasNext = true)
            }
        }

        val cards = searchCards(page, "main", sortTerm)
        return newHomePageResponse(request.name, cards.map { it.toResult() }, hasNext = cards.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (query.isBlank()) return emptyList<SearchResponse>().toNewSearchResponseList(false)
        val cards = searchCards(page, "name", query.trim())
        return cards.map { it.toResult() }.toNewSearchResponseList(hasNext = cards.size >= 10)
    }

    private suspend fun searchCards(page: Int, type: String, term: String): List<OgladajAnimeParser.Card> {
        val res = siteRequest("$mainUrl/manager.php?action=get_search", mapOf(
            "page" to (page - 1).toString(), "search_type" to type, "search" to term
        ))
        val html = tryParseJson<AjaxResponse>(res.text)?.data ?: return emptyList()
        return OgladajAnimeParser.cardsFromHtml(html, URI(res.url).resolve("/").toString())
    }

    override suspend fun load(url: String): LoadResponse {
        val page = siteRequest(url)
        val detail = OgladajAnimeParser.details(OgladajAnimeParser.document(page.text, page.url), page.url)
        if (detail.episodes.isEmpty()) throw ErrorLoadingException("OglądajAnime: dla tego tytułu nie znaleziono odcinków.")

        val type = contentType(detail.type)
        return if (type == TvType.AnimeMovie && detail.episodes.size == 1) {
            newMovieLoadResponse(detail.title, page.url, type, detail.episodes.first().url) {
                posterUrl = detail.poster
                backgroundPosterUrl = detail.poster
                plot = detail.plot
                year = detail.year
                tags = detail.genres
            }
        } else {
            newAnimeLoadResponse(detail.title, page.url, type) {
                posterUrl = detail.poster
                backgroundPosterUrl = detail.poster
                plot = detail.plot
                year = detail.year
                tags = detail.genres
                showStatus = when {
                    detail.status?.contains("Zakończone", ignoreCase = true) == true -> ShowStatus.Completed
                    detail.status?.contains("Emitowane", ignoreCase = true) == true ||
                    detail.status?.contains("W trakcie", ignoreCase = true) == true -> ShowStatus.Ongoing
                    else -> null
                }
                addEpisodes(DubStatus.Subbed, detail.episodes.map { ep ->
                    newEpisode(ep.url) {
                        name = ep.title
                        episode = ep.episode?.takeIf { it % 1.0 == 0.0 }?.toInt()
                    }
                })
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epId = Regex("""(?:watchepisode=|ep_id=)(\d+)""").find(data)?.groupValues?.get(1)
            ?: data.trim().takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
            ?: throw ErrorLoadingException("OglądajAnime: brak identyfikatora odcinka.")

        val listPost = mapOf(
            "id" to epId,
            "error" to "300030",
            "token" to ""
        )
        val listRes = siteRequest("$mainUrl/manager.php?action=get_player_list", listPost)

        val outerJson = tryParseJson<AjaxResponse>(listRes.text)
        val payloadJson = outerJson?.data?.let { tryParseJson<PlayerListPayload>(it) }

        var found = false

        // 1. Check default player URL
        payloadJson?.url?.takeIf { it.startsWith("http") && !it.startsWith("*") }?.let { defaultUrl ->
            try {
                loadExtractor(defaultUrl, "$mainUrl/", subtitleCallback) { link ->
                    found = true
                    callback(link)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }

        // 2. Query individual player mirrors
        val players = payloadJson?.players ?: emptyList()
        for (player in players) {
            val pid = player.id ?: continue
            val mirrorPost = mapOf(
                "id" to pid.toString(),
                "error" to "300030",
                "token" to ""
            )
            try {
                val mirrorRes = siteRequest("$mainUrl/manager.php?action=change_player_url", mirrorPost)
                val mirrorJson = tryParseJson<AjaxResponse>(mirrorRes.text)
                val embedUrl = mirrorJson?.data?.trim()
                if (!embedUrl.isNullOrEmpty() && embedUrl.startsWith("http") && !embedUrl.startsWith("*")) {
                    if (embedUrl.contains(".mp4") || embedUrl.contains(".m3u8")) {
                        val kind = if (embedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        val label = "${player.url ?: "OglądajAnime"} (${player.quality ?: "wideo"})"
                        callback(newExtractorLink(name, label, embedUrl, kind) {
                            referer = "$mainUrl/"
                        })
                        found = true
                    } else {
                        loadExtractor(embedUrl, "$mainUrl/", subtitleCallback) { link ->
                            found = true
                            callback(link)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }

        if (!found) {
            throw ErrorLoadingException("OglądajAnime: nie znaleziono aktywnego źródła wideo dla tego odcinka.")
        }

        return true
    }
}
