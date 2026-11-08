package pl.ogladajanimecloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.CancellationException

class OgladajAnimeProvider : MainAPI() {
    override var mainUrl = "https://ogladajanime.pl"
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

    @Volatile
    private var sessionCookie: String? = null

    private suspend fun ensureSession(): String {
        sessionCookie?.let { return it }
        val res = app.get(mainUrl, headers = mapOf("Accept-Language" to "pl,en;q=0.7"))
        val cookie = res.headers["set-cookie"]?.split(";")?.firstOrNull()?.trim()
            ?: res.okhttpResponse.headers("Set-Cookie").firstOrNull()?.split(";")?.firstOrNull()?.trim()
        if (!cookie.isNullOrEmpty()) {
            sessionCookie = cookie
            return cookie
        }
        return ""
    }

    private suspend fun ajaxHeaders(): Map<String, String> {
        val cookie = ensureSession()
        val headers = mutableMapOf(
            "Referer" to "$mainUrl/",
            "X-Requested-With" to "XMLHttpRequest",
            "Accept-Language" to "pl,en;q=0.7"
        )
        if (cookie.isNotEmpty()) {
            headers["Cookie"] = cookie
        }
        return headers
    }

    private suspend fun sitePage(url: String): Document {
        if (!OgladajAnimeParser.isSiteUrl(url)) throw ErrorLoadingException("OglądajAnime: nieprawidłowy adres strony.")
        val cookie = ensureSession()
        val headers = mutableMapOf("Accept-Language" to "pl,en;q=0.7")
        if (cookie.isNotEmpty()) headers["Cookie"] = cookie
        val response = app.get(url, headers = headers)
        if (response.code == 403 || response.code == 429) {
            throw ErrorLoadingException("OglądajAnime: strona odrzuciła połączenie (HTTP ${response.code}). Sprawdź dostęp w przeglądarce.")
        }
        if (response.code !in 200..299) throw ErrorLoadingException("OglądajAnime: błąd HTTP ${response.code}.")
        return OgladajAnimeParser.document(response.text, url)
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
        val searchType = when (request.data) {
            "top" -> "main"
            "popular" -> "main"
            else -> "main"
        }
        val sortTerm = when (request.data) {
            "top" -> "rate"
            "popular" -> "pop"
            else -> "date"
        }

        // On first page of "recent", the home page HTML contains freshly added series
        if (page == 1 && request.data == "recent") {
            val doc = sitePage(mainUrl)
            val cards = OgladajAnimeParser.cards(doc, mainUrl)
            if (cards.isNotEmpty()) {
                return newHomePageResponse(request.name, cards.map { it.toResult() }, hasNext = true)
            }
        }

        val postData = mapOf(
            "page" to (page - 1).toString(),
            "search_type" to searchType,
            "search" to sortTerm
        )
        val res = app.post("$mainUrl/manager.php?action=get_search", data = postData, headers = ajaxHeaders())
        if (res.code !in 200..299) throw ErrorLoadingException("OglądajAnime: błąd podczas pobierania katalogu (HTTP ${res.code}).")

        val json = tryParseJson<AjaxResponse>(res.text)
        val cards = json?.data?.let { OgladajAnimeParser.cardsFromHtml(it, mainUrl) } ?: emptyList()
        return newHomePageResponse(request.name, cards.map { it.toResult() }, hasNext = cards.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (query.isBlank()) return emptyList<SearchResponse>().toNewSearchResponseList(false)
        val postData = mapOf(
            "page" to (page - 1).toString(),
            "search_type" to "name",
            "search" to query.trim()
        )
        val res = app.post("$mainUrl/manager.php?action=get_search", data = postData, headers = ajaxHeaders())
        if (res.code !in 200..299) return emptyList<SearchResponse>().toNewSearchResponseList(false)

        val json = tryParseJson<AjaxResponse>(res.text)
        val cards = json?.data?.let { OgladajAnimeParser.cardsFromHtml(it, mainUrl) } ?: emptyList()
        return cards.map { it.toResult() }.toNewSearchResponseList(hasNext = cards.size >= 10)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = sitePage(url)
        val detail = OgladajAnimeParser.details(doc, url)
        if (detail.episodes.isEmpty()) throw ErrorLoadingException("OglądajAnime: dla tego tytułu nie znaleziono odcinków.")

        val type = contentType(detail.type)
        return if (type == TvType.AnimeMovie && detail.episodes.size == 1) {
            newMovieLoadResponse(detail.title, url, type, detail.episodes.first().url) {
                posterUrl = detail.poster
                backgroundPosterUrl = detail.poster
                plot = detail.plot
                year = detail.year
                tags = detail.genres
            }
        } else {
            newAnimeLoadResponse(detail.title, url, type) {
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
            ?: data.trim().takeIf { it.all { ch -> ch.isDigit() } }
            ?: throw ErrorLoadingException("OglądajAnime: brak identyfikatora odcinka.")

        val listPost = mapOf(
            "id" to epId,
            "error" to "300030",
            "token" to ""
        )
        val listRes = app.post("$mainUrl/manager.php?action=get_player_list", data = listPost, headers = ajaxHeaders())
        if (listRes.code !in 200..299) throw ErrorLoadingException("OglądajAnime: błąd serwera (HTTP ${listRes.code}).")

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
                val mirrorRes = app.post("$mainUrl/manager.php?action=change_player_url", data = mirrorPost, headers = ajaxHeaders())
                if (mirrorRes.code in 200..299) {
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
