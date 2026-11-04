package pl.ekinocloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URI

class EkinoProvider(private val session: EkinoSession) : MainAPI() {
    override var mainUrl = EkinoParser.BASE
    override var name = "Ekino"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val loadLinksTimeoutMs = 180_000L
    override val mainPage = mainPageOf("/movie/cat/" to "Najnowsze filmy", "/serie/" to "Seriale")
    private data class Page(val url: String, val doc: org.jsoup.nodes.Document)

    private suspend fun sitePage(url: String, referer: String = "$mainUrl/", form: Map<String, String>? = null,
                                 allowMissing: Boolean = false): Page? {
        var target = url
        repeat(6) { redirect ->
            if (!EkinoParser.isSiteUrl(target)) throw ErrorLoadingException("Ekino: nieobsługiwana domena przekierowania.")
            val headers = buildMap {
                put("User-Agent", session.userAgent().ifBlank { USER_AGENT })
                put("Referer", referer)
                session.cookieHeader(target).takeIf(String::isNotBlank)?.let { put("Cookie", it) }
            }
            val response = if (form != null && redirect == 0) app.post(target, data = form, headers = headers,
                allowRedirects = false, cacheTime = 0) else app.get(target, headers = headers, allowRedirects = false, cacheTime = 0)
            session.mergeResponseCookies(target, response.headers.values("Set-Cookie"))
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                // No credentials are forwarded across domains, including unannounced mirror domains.
                target = response.headers["Location"]?.let { EkinoParser.absolute(target, it) }
                    ?: throw ErrorLoadingException("Ekino: niepoprawne przekierowanie.")
                if (URI(target).path.startsWith("/login")) throw ErrorLoadingException("Ekino: zaloguj się w ustawieniach dodatku i zapisz sesję.")
            } else {
                if (response.code in listOf(401, 403, 429)) throw ErrorLoadingException(when (response.code) {
                    401 -> "Ekino: wymagane logowanie. Otwórz zębatkę dodatku i zapisz sesję."
                    403 -> "Ekino: serwis odmówił dostępu (HTTP 403). Sprawdź stronę w ustawieniach dodatku; blokada nie jest automatycznie ponawiana."
                    else -> "Ekino: zbyt wiele żądań (HTTP 429). Spróbuj później."
                })
                if (allowMissing && response.code in listOf(404, 410)) return null
                if (response.code !in 200..299) throw ErrorLoadingException("Ekino: błąd serwera HTTP ${response.code}.")
                val doc = EkinoParser.document(response.text, target)
                if (EkinoParser.isChallenge(doc)) throw ErrorLoadingException("Ekino wymaga weryfikacji lub odmówiło dostępu. Sprawdź stronę w ustawieniach dodatku.")
                if (EkinoParser.isLoginPage(doc)) throw ErrorLoadingException("Ekino: zaloguj się w ustawieniach dodatku i zapisz sesję.")
                return Page(target, doc)
            }
        }
        throw ErrorLoadingException("Ekino: zbyt wiele przekierowań.")
    }

    private fun EkinoParser.Card.toResult(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; year = this@toResult.year }
    } else newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; year = this@toResult.year }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data !in setOf("/movie/cat/", "/serie/")) throw ErrorLoadingException("Ekino: nieznana kategoria.")
        if (request.data == "/serie/" && page > 1) return newHomePageResponse(request.name, emptyList(), false)
        val target = "$mainUrl${request.data}" + if (page > 1) "strona%5B$page%5D+" else ""
        val result = sitePage(target)!!
        val cards = EkinoParser.cards(result.doc, result.url)
        if (cards.isEmpty() && page == 1) throw ErrorLoadingException("Ekino: nie znaleziono katalogu. Strona mogła zmienić układ.")
        return newHomePageResponse(request.name, cards.map { it.toResult() }, EkinoParser.nextPage(result.doc, target) != null)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        if (query.isBlank() || page > 1) return emptyList<SearchResponse>().toNewSearchResponseList(false)
        // The site's actual search form uses POST /search/qf with q, not /wyszukiwarka?phrase=.
        val result = sitePage("$mainUrl/search/qf", form = mapOf("q" to query))!!
        return EkinoParser.cards(result.doc, result.url).map { it.toResult() }.toNewSearchResponseList(false)
    }

    override suspend fun load(url: String): LoadResponse {
        val target = EkinoParser.siteUrl(mainUrl, url)?.takeIf(EkinoParser::isDetailUrl)
            ?: throw ErrorLoadingException("Ekino: niepoprawny adres filmu lub serialu.")
        val result = sitePage(target)!!
        val details = try { EkinoParser.details(result.doc, result.url) }
            catch (_: IllegalArgumentException) { throw ErrorLoadingException("Ekino: nie znaleziono szczegółów materiału.") }
        return if (URI(target).path.startsWith("/serie/")) {
            if (details.episodes.isEmpty()) throw ErrorLoadingException("Ekino: brak dostępnych odcinków serialu.")
            newTvSeriesLoadResponse(details.title, target, TvType.TvSeries, details.episodes.map { ep ->
                newEpisode(ep.url) { name = ep.title; season = ep.season; episode = ep.episode }
            }) { posterUrl = details.poster; plot = details.plot; year = details.year; tags = details.tags }
        } else newMovieLoadResponse(details.title, target, TvType.Movie, target) {
            posterUrl = details.poster; plot = details.plot; year = details.year; tags = details.tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
                                   callback: (ExtractorLink) -> Unit): Boolean {
        val target = EkinoParser.siteUrl(mainUrl, data)?.takeIf(EkinoParser::isDetailUrl)
            ?: throw ErrorLoadingException("Ekino: niepoprawny adres odcinka.")
        val page = sitePage(target)!!
        val players = EkinoParser.players(page.doc, page.url).take(12)
        if (players.isEmpty()) throw ErrorLoadingException("Ekino: brak zwykłych odtwarzaczy lub zmieniony układ strony. Sprawdź materiał na stronie.")
        // Respect the countdown advertised by the free-player UI. Never automate verification or premium access.
        delay((EkinoParser.playerDelaySeconds(page.doc) ?: 10) * 1_000L)
        var found = false
        val seen = mutableSetOf<String>()
        for (player in players) {
            // Site denials abort the whole operation; they are never swallowed as extractor failures.
            val wrapper = sitePage(player.url, page.url, allowMissing = true) ?: continue
            val first = EkinoParser.embeds(wrapper.doc, wrapper.url)
            val embeds = mutableListOf<EkinoParser.Embed>()
            for (embed in first.take(8)) {
                if (EkinoParser.isSiteUrl(embed.url)) {
                    val nested = sitePage(embed.url, wrapper.url, allowMissing = true) ?: continue
                    embeds.addAll(EkinoParser.embeds(nested.doc, nested.url).filterNot { EkinoParser.isSiteUrl(it.url) })
                } else embeds.add(embed)
            }
            for (embed in embeds.distinctBy { it.url }.take(12)) {
                try {
                    val links = mutableListOf<ExtractorLink>()
                    if (embed.direct) links.add(newExtractorLink(name, player.host, embed.url,
                        if (URI(embed.url).path.endsWith(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        referer = wrapper.url
                    }) else loadExtractor(embed.url, wrapper.url, subtitleCallback) { links.add(it) }
                    for (link in links) {
                        if (!seen.add("${link.url}|${player.language}")) continue
                        found = true
                        callback(newExtractorLink(name, listOf(link.name, player.language).filter(String::isNotBlank).joinToString(" · "), link.url, link.type) {
                            referer = link.referer; headers = link.headers; quality = link.quality; extractorData = link.extractorData
                        })
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Try other ordinary hosts. No session or player URL logging. */ }
            }
        }
        if (!found) throw ErrorLoadingException("Ekino: nie uzyskano wideo z dostępnych odtwarzaczy. Sprawdź materiał na stronie; hosting może wymagać logowania lub być nieobsługiwany w CloudStream.")
        return true
    }
}
