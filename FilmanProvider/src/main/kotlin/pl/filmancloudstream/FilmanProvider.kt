package pl.filmancloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.concurrent.CancellationException
import java.net.URI
import java.net.URLEncoder

class FilmanProvider(private val session: FilmanSession) : MainAPI() {
    override var mainUrl = FilmanParser.BASE
    override var name = "Filman — konto darmowe"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "/filmy/popularne" to "Popularne filmy",
        "/seriale/popularne" to "Popularne seriale",
        "/filmy/" to "Filmy",
        "/seriale/" to "Seriale"
    )

    private data class Page(val url: String, val text: String, val location: String?)
    private data class TokenResponse(@param:JsonProperty("ok") val ok: Boolean = false,
                                     @param:JsonProperty("url") val url: String? = null)

    private fun headers(url: String, referer: String?, ajax: Boolean): Map<String, String> = buildMap {
        put("User-Agent", session.userAgent().ifBlank { USER_AGENT })
        put("Accept-Language", "pl,en;q=0.7")
        session.cookieHeader(url).takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        referer?.let { put("Referer", it) }
        if (ajax) put("X-Requested-With", "XMLHttpRequest")
    }

    private suspend fun sitePage(url: String, referer: String? = mainUrl, ajax: Boolean = false,
                                 follow: Boolean = true, form: Map<String, String>? = null): Page {
        if (!session.hasSession()) throw ErrorLoadingException("Filman: otwórz zębatkę dodatku w Rozszerzeniach, zaloguj się i zapisz sesję.")
        var target = url
        repeat(6) {
            if (!FilmanParser.isSiteUrl(target)) throw ErrorLoadingException("Filman przekierował poza obsługiwaną domenę filman.cc.")
            val response = if (form == null || it > 0) {
                app.get(target, headers = headers(target, referer, ajax), allowRedirects = false, cacheTime = 0)
            } else {
                app.post(target, data = form, headers = headers(target, referer, ajax), allowRedirects = false, cacheTime = 0)
            }
            session.mergeResponseCookies(target, response.headers.values("Set-Cookie"))
            val location = response.headers["Location"]?.let { raw -> FilmanParser.absolute(target, raw) }
            if (response.code in 300..399 && location != null) {
                if (URI(location).path.startsWith("/logowanie")) throw ErrorLoadingException("Filman: sesja wygasła. Zaloguj się ponownie w ustawieniach dodatku.")
                if (!follow) return Page(target, response.text, location)
                target = location
            } else {
                if (response.code == 401) throw ErrorLoadingException("Filman: wymagane ponowne logowanie w ustawieniach dodatku.")
                if (response.code == 403 || response.code == 429) throw ErrorLoadingException("Filman odrzucił połączenie (${response.code}). Otwórz logowanie dodatku i sprawdź stronę; nie ponawiaj wielokrotnie żądań.")
                if (response.code !in 200..299) throw ErrorLoadingException("Filman: błąd serwera HTTP ${response.code}.")
                val doc = FilmanParser.document(response.text, target)
                if (FilmanParser.isLoginPage(doc)) throw ErrorLoadingException("Filman: sesja wygasła lub nie została zalogowana. Otwórz ustawienia dodatku.")
                if (FilmanParser.isChallenge(doc)) throw ErrorLoadingException("Filman wymaga weryfikacji w oknie logowania dodatku.")
                return Page(target, response.text, null)
            }
        }
        throw ErrorLoadingException("Filman: zbyt wiele przekierowań.")
    }

    private fun FilmanParser.Card.toResult(): SearchResponse {
        // Both builders are available on stable CloudStream versions.
        return if (series) newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
            posterUrl = poster; year = this@toResult.year
        } else newMovieSearchResponse(title, url, TvType.Movie) {
            posterUrl = poster; year = this@toResult.year
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val separator = if (request.data.contains('?')) "&" else "?"
        val target = "$mainUrl${request.data}${separator}page=$page"
        val result = sitePage(target)
        val doc = FilmanParser.document(result.text, result.url)
        val root = doc.selectFirst("#item-list, #series-list") ?: doc
        val cards = FilmanParser.cards(root, result.url)
        if (page == 1 && cards.isEmpty()) throw ErrorLoadingException("Filman: brak katalogu. Sprawdź sesję w ustawieniach dodatku; strona mogła zmienić układ.")
        return newHomePageResponse(request.name, cards.map { it.toResult() }, FilmanParser.nextPage(doc, result.url) != null)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        if (query.isBlank()) return emptyList<SearchResponse>().toNewSearchResponseList(false)
        val result = sitePage("$mainUrl/search?phrase=${encode(query)}&page=$page")
        val doc = FilmanParser.document(result.text, result.url)
        var cards = FilmanParser.cards(doc, result.url)
        if (cards.isEmpty() && page == 1) {
            val csrf = FilmanParser.csrf(doc)
            if (csrf != null) {
                val ajax = sitePage("$mainUrl/szukam", ajax = true, form = mapOf("phrase" to query, "_csrf" to csrf))
                cards = FilmanParser.cards(FilmanParser.document(ajax.text, mainUrl), mainUrl)
            }
        }
        return cards.map { it.toResult() }.toNewSearchResponseList(FilmanParser.nextPage(doc, result.url) != null)
    }

    override suspend fun load(url: String): LoadResponse {
        val result = sitePage(url)
        val details = FilmanParser.details(FilmanParser.document(result.text, result.url), result.url)
        return if (details.episodes.isNotEmpty() || URI(url).path.startsWith("/s/")) {
            if (details.episodes.isEmpty()) throw ErrorLoadingException("Filman: nie znaleziono listy odcinków. Sprawdź sesję lub stronę serialu.")
            newTvSeriesLoadResponse(details.title, url, TvType.TvSeries, details.episodes.map { ep ->
                newEpisode(ep.url) { name = ep.title; season = ep.season; episode = ep.episode }
            }) {
                posterUrl = details.poster
                backgroundPosterUrl = details.backdrop ?: details.poster
                plot = details.plot
                year = details.year
                tags = details.tags
            }
        } else {
            newMovieLoadResponse(details.title, url, TvType.Movie, url) {
                posterUrl = details.poster
                backgroundPosterUrl = details.backdrop ?: details.poster
                plot = details.plot
                year = details.year
                tags = details.tags
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean,
                                   subtitleCallback: (SubtitleFile) -> Unit,
                                   callback: (ExtractorLink) -> Unit): Boolean {
        val page = sitePage(data)
        val doc = FilmanParser.document(page.text, page.url)
        val players = FilmanParser.players(doc, page.url)
        val route = FilmanParser.routeToken(page.text)
        if (players.isEmpty()) throw ErrorLoadingException("Filman: brak zwykłych linków dla darmowego konta albo zmieniony układ strony.")
        var found = false
        var unavailable = 0
        for (player in players.take(30)) {
            var target = player.url
            // Authentication, challenge and rate-limit failures belong to the site, not an individual host.
            // Keep these outside the extractor try/catch so the user receives the login/error message.
            if (FilmanParser.isSiteUrl(target)) {
                if (!URI(target).path.startsWith("/link/go/") || route.isNullOrBlank()) { unavailable++; continue }
                val id = URI(target).path.substringAfterLast('/').takeIf { it.matches(Regex("\\d+")) } ?: continue
                // Same two requests as Filman's own ordinary player click, within this user's session.
                sitePage(target, page.url, follow = false)
                val tokenPage = sitePage("$mainUrl/link/token?link_id=$id&rt=${encode(route)}", page.url, ajax = true)
                val token = tryParseJson<TokenResponse>(tokenPage.text)
                if (token?.ok != true || token.url == null) { unavailable++; continue }
                target = FilmanParser.decodeBase64(token.url)?.let { FilmanParser.absolute(page.url, it) } ?: continue
            }
            try {
                // The wrapper is a normal public embed. Filman session is never sent to its host.
                if (URI(target).host == "embed.tmp-url.pro") {
                    val wrapper = app.get(target, headers = mapOf("User-Agent" to session.userAgent(), "Referer" to "$mainUrl/"),
                        cacheTime = 0).text
                    val unwrapped = FilmanParser.unwrapHost(wrapper, target)
                    if (unwrapped == null) { unavailable++; continue }
                    target = unwrapped
                }
                if (FilmanParser.isSiteUrl(target)) { unavailable++; continue }
                loadExtractor(target, "$mainUrl/", subtitleCallback) { link ->
                    found = true
                    callback(FilmanLinkLabels.apply(link, player))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // One failed host must not hide other working players. Never log token URLs/cookies.
                unavailable++
            }
        }
        if (!found) throw ErrorLoadingException("Filman: nie uzyskano wideo. Sprawdź odtwarzanie na stronie. Host może być nieobsługiwany; liczba pominiętych źródeł: $unavailable.")
        return true
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
