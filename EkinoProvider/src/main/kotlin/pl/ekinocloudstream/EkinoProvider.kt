package pl.ekinocloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.extractorApis
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

    private suspend fun <T> guardedRequest(block: suspend () -> T): T = try {
        session.requestGate.request(block)
    } catch (_: EkinoRequestGate.Paused) {
        throw ErrorLoadingException(EkinoMessages.REQUESTS_PAUSED)
    }

    private suspend fun sitePage(url: String, referer: String = "$mainUrl/", form: Map<String, String>? = null,
                                 allowMissing: Boolean = false,
                                 cookies: EkinoRequestCookies = EkinoRequestCookies(session.cookieHeader(mainUrl))): Page? = guardedRequest {
        var target = url
        repeat(6) { redirect ->
            if (!EkinoParser.isSiteUrl(target)) throw ErrorLoadingException("Ekino: nieobsługiwana domena przekierowania.")
            val headers = buildMap {
                put("User-Agent", session.userAgent().ifBlank { USER_AGENT })
                put("Referer", referer)
                cookies.header(target).takeIf(String::isNotBlank)?.let { put("Cookie", it) }
            }
            val response = if (form != null && redirect == 0) app.post(target, data = form, headers = headers,
                allowRedirects = false, cacheTime = 0) else app.get(target, headers = headers, allowRedirects = false, cacheTime = 0)
            val setCookie = response.headers.values("Set-Cookie")
            cookies.merge(target, setCookie)
            session.mergeResponseCookies(target, setCookie)
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                // No credentials are forwarded across domains, including unannounced mirror domains.
                target = response.headers["Location"]?.let { EkinoParser.absolute(target, it) }
                    ?: throw ErrorLoadingException("Ekino: niepoprawne przekierowanie.")
                if (URI(target).path.startsWith("/login")) throw ErrorLoadingException("Ekino: zaloguj się w ustawieniach dodatku i zapisz sesję.")
            } else {
                if (response.code in listOf(403, 429)) session.requestGate.pause()
                if (response.code in listOf(401, 403, 429)) throw ErrorLoadingException(when (response.code) {
                    401 -> "Ekino: wymagane logowanie. Otwórz zębatkę dodatku i zapisz sesję."
                    403 -> "Ekino: serwis odmówił dostępu (HTTP 403). Jeśli Cloudflare nie przechodzi, spróbuj zmienić internet (Wi-Fi / dane komórkowe)."
                    else -> "Ekino: zbyt wiele żądań (HTTP 429). Spróbuj później."
                })
                if (allowMissing && response.code in listOf(404, 410)) return@guardedRequest null
                if (response.code !in 200..299) throw ErrorLoadingException("Ekino: błąd serwera HTTP ${response.code}.")
                val doc = EkinoParser.document(response.text, target)
                if (EkinoParser.isChallenge(doc)) {
                    session.requestGate.pause()
                    throw ErrorLoadingException(EkinoMessages.CLOUDFLARE)
                }
                if (URI(target).path.startsWith("/watch/f/") && EkinoParser.isPlayerVerification(doc)) session.requestGate.pause()
                if (EkinoParser.isLoginPage(doc)) throw ErrorLoadingException("Ekino: zaloguj się w ustawieniach dodatku i zapisz sesję.")
                return@guardedRequest Page(target, doc)
            }
        }
        throw ErrorLoadingException("Ekino: zbyt wiele przekierowań.")
    }

    /** The public bridge has its own origin. It never receives the Ekino cookie jar. */
    private suspend fun bridgeEmbeds(url: String): List<EkinoParser.Embed> = guardedRequest {
        var target = url
        repeat(3) {
            if (!EkinoParser.isBridgeUrl(target)) return@guardedRequest emptyList()
            val response = app.get(target, headers = mapOf(
                "User-Agent" to session.userAgent().ifBlank { USER_AGENT }, "Referer" to "$mainUrl/"),
                allowRedirects = false, cacheTime = 0)
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val next = response.headers["Location"]?.let { EkinoParser.absolute(target, it) } ?: return@guardedRequest emptyList()
                if (!EkinoParser.isBridgeUrl(next)) {
                    // A redirect to a hosting URL is handed to its extractor, without our credentials.
                    return@guardedRequest if (EkinoParser.isSiteUrl(next)) emptyList() else listOf(EkinoParser.Embed(next, false, target))
                }
                target = next
            } else {
                if (response.code in listOf(403, 429)) session.requestGate.pause()
                if (response.code in listOf(401, 403, 429)) {
                    val hint = if (response.code == 403) " Jeśli Cloudflare nie przechodzi, spróbuj zmienić internet (Wi-Fi / dane komórkowe)."
                        else " Sprawdź odtwarzanie na stronie; żądanie nie jest automatycznie ponawiane."
                    throw ErrorLoadingException("Ekino: strona pośrednia odmówiła dostępu (HTTP ${response.code}).$hint")
                }
                if (response.code !in 200..299) return@guardedRequest emptyList()
                val doc = EkinoParser.document(response.text, target)
                if (EkinoParser.isChallenge(doc)) {
                    session.requestGate.pause()
                    throw ErrorLoadingException(EkinoMessages.CLOUDFLARE)
                }
                if (EkinoParser.isPlayerVerification(doc)) {
                    session.requestGate.pause()
                    throw ErrorLoadingException(EkinoMessages.BRIDGE_VERIFICATION)
                }
                return@guardedRequest EkinoParser.embeds(doc, target).filterNot { EkinoParser.isSiteUrl(it.url) || EkinoParser.isBridgeUrl(it.url) }
            }
        }
        return@guardedRequest emptyList()
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
        session.rememberVerificationPage(target)
        val cookies = EkinoRequestCookies(session.cookieHeader(mainUrl))
        val page = sitePage(target, cookies = cookies)!!
        val players = EkinoParser.players(page.doc, page.url).take(12)
        val delaySeconds = EkinoParser.playerDelaySeconds(page.doc)
        if (players.isEmpty() || delaySeconds == null) throw ErrorLoadingException("Ekino: brak zwykłych odtwarzaczy lub zmieniony układ strony. Sprawdź materiał na stronie.")
        // Respect the countdown advertised by the free-player UI. Never automate verification or premium access.
        delay(delaySeconds * 1_000L)
        var found = false
        var missing = 0
        var noEmbeds = 0
        var failedBridges = 0
        var extractorAttempts = 0
        val seen = mutableSetOf<String>()
        val publicHostHttp = EkinoHostHttp(session.userAgent().ifBlank { USER_AGENT })
        var activePlayer: EkinoParser.Player? = null
        suspend fun emitLink(link: ExtractorLink) {
            val player = activePlayer ?: return
            if (!seen.add("${link.url}|${player.language}")) return
            found = true
            callback(newExtractorLink(name, listOf(link.name, player.language).filter(String::isNotBlank).joinToString(" · "), link.url, link.type) {
                referer = link.referer; headers = link.headers; quality = link.quality; extractorData = link.extractorData
            })
        }
        val hostResolver = EkinoHostResolver(
            extractor = { url, referer ->
                extractorAttempts++
                val links = mutableListOf<ExtractorLink>()
                val hostname = URI(url).host
                val exactHost = extractorApis.any { runCatching { URI(it.mainUrl).host.equals(hostname, true) }.getOrDefault(false) }
                // Upzone has no built-in extractor in the inspected CloudStream version.
                // Do not send it to an unrelated fuzzy match; use its ordinary source metadata instead.
                val upzone = hostname.orEmpty().split('.').any { it.equals("upzone", true) }
                val supported = if (upzone && !exactHost) false else loadExtractor(url, referer, subtitleCallback) { links.add(it) }
                for (link in links) emitLink(link)
                when {
                    links.isNotEmpty() -> EkinoHostResolver.Dispatch.FOUND
                    supported -> EkinoHostResolver.Dispatch.EMPTY
                    else -> EkinoHostResolver.Dispatch.UNSUPPORTED
                }
            },
            fetch = publicHostHttp::get,
            emit = { media ->
                val label = activePlayer?.host ?: "Ekino"
                emitLink(newExtractorLink(name, label, media.url,
                    if (media.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = media.referer; headers = publicHostHttp.mediaHeaders(media.url)
                })
            },
            unpack = { getAndUnpack(it) })
        try {
            for (player in players) {
                activePlayer = player
                hostResolver.nextPlayer()
                // Site denials stop further requests; previously emitted links remain usable.
                val wrapper = sitePage(player.url, page.url, allowMissing = true, cookies = cookies)
                if (wrapper == null) { missing++; continue }
                if (EkinoParser.isPlayerVerification(wrapper.doc)) {
                    throw ErrorLoadingException(EkinoMessages.playerVerification(player.host))
                }
                val first = EkinoParser.embeds(wrapper.doc, wrapper.url)
                if (first.isEmpty()) noEmbeds++
                val embeds = mutableListOf<EkinoParser.Embed>()
                for (embed in first.take(8)) {
                    if (EkinoParser.isSiteUrl(embed.url)) {
                        val nested = sitePage(embed.url, wrapper.url, allowMissing = true, cookies = cookies) ?: continue
                        if (EkinoParser.isPlayerVerification(nested.doc)) {
                            throw ErrorLoadingException(EkinoMessages.playerVerification(player.host))
                        }
                        embeds.addAll(EkinoParser.embeds(nested.doc, nested.url).filterNot { EkinoParser.isSiteUrl(it.url) })
                    } else embeds.add(embed)
                }
                val resolved = mutableListOf<EkinoParser.Embed>()
                for (embed in embeds.distinctBy { it.url }.take(12)) {
                    if (EkinoParser.isBridgeUrl(embed.url)) {
                        val unwrapped = try { bridgeEmbeds(embed.url) }
                        catch (error: CancellationException) { throw error }
                        catch (error: ErrorLoadingException) { throw error }
                        catch (_: Exception) { emptyList() }
                        if (unwrapped.isEmpty()) failedBridges++
                        resolved.addAll(unwrapped)
                    } else resolved.add(embed)
                }
                for (embed in resolved.distinctBy { it.url }.take(12)) {
                    if (embed.direct) {
                        emitLink(newExtractorLink(name, player.host, embed.url,
                            if (URI(embed.url).path.endsWith(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            referer = embed.referer
                        })
                    } else hostResolver.resolve(embed.url, embed.referer)
                }
            }
        } catch (error: ErrorLoadingException) {
            // Stop at a denial without further requests, but retain any source already emitted.
            if (!found) throw error
            return true
        }
        if (!found) throw ErrorLoadingException("Ekino: nie uzyskano wideo. Źródła: ${players.size}, niedostępne: $missing, bez odnośnika: $noEmbeds, błędy strony pośredniej: $failedBridges, próby ekstraktorów: $extractorAttempts. Hostingi: ${hostResolver.report().ifBlank { "brak" }}." +
            hostResolver.verificationHint().takeIf(String::isNotBlank)?.let { " $it" }.orEmpty())
        return true
    }
}
