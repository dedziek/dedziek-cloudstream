package pl.dedziek.ekinotv

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

@CloudstreamPlugin
class EkinoTvPlugin : Plugin() {
    override fun load(context: Context) {
        val browser = WebSession(context.applicationContext, SessionState(context.applicationContext))
        registerMainAPI(EkinoTvProvider(browser))
        openSettings = { browser.settings(it) }
    }
}

internal class EkinoTvProvider(private val browser: WebSession) : MainAPI() {
    override var mainUrl = Site.ENTRY
    override var name = "Ekino TV"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val loadLinksTimeoutMs = 180_000L
    override val mainPage = mainPageOf("/movie/cat/" to "Najnowsze filmy", "/serie/" to "Seriale")
    private val metadata = linkedMapOf<String, Pair<Long, WebPage>>()

    private suspend fun <T> operation(block: suspend () -> T): T = browser.operations.withLock {
        browser.state.rejection()?.let { throw ErrorLoadingException(it) }
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: ErrorLoadingException) { throw error }
        catch (error: SiteProblem) { throw ErrorLoadingException(error.message.orEmpty()) }
        catch (_: Exception) { throw ErrorLoadingException("Ekino TV: nie można odczytać strony. Sprawdź raport w ustawieniach dodatku.") }
    }

    private suspend fun material(url: String): WebPage {
        require(Site.material(url))
        browser.state.remember(url)
        val now = android.os.SystemClock.elapsedRealtime()
        metadata.entries.removeAll { now - it.value.first > 60_000 }
        metadata[url]?.let { return it.second }
        val page = browser.page(url)
        if (!Site.material(page.url)) throw ErrorLoadingException("Ekino TV: materiał przekierował do innej strony. Otwórz stronę / logowanie w ustawieniach dodatku.")
        if (metadata.size >= 5) metadata.remove(metadata.keys.first())
        metadata[url] = now to page
        return page
    }

    private fun Card.result(): SearchResponse = if (series) newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
        posterUrl = poster; year = this@result.year
    } else newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; year = this@result.year }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = operation {
        if (request.data !in setOf("/movie/cat/", "/serie/") || page !in 1..1000)
            throw ErrorLoadingException("Ekino TV: nieznana kategoria.")
        val target = "$mainUrl${request.data}" + if (page > 1) "strona%5B$page%5D+" else ""
        val result = browser.page(target)
        mainUrl = Site.origin(result.url)
        val cards = Catalogue.cards(result)
        if (page == 1 && cards.isEmpty()) throw ErrorLoadingException("Ekino TV: brak katalogu. Otwórz stronę w ustawieniach dodatku i sprawdź dostęp.")
        newHomePageResponse(request.name, cards.map { it.result() }, Catalogue.hasNext(result))
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? = operation {
        if (query.isBlank() || page > 1) return@operation emptyList<SearchResponse>().toNewSearchResponseList(false)
        val result = browser.search(query)
        mainUrl = Site.origin(result.url)
        Catalogue.cards(result).map { it.result() }.toNewSearchResponseList(false)
    }

    override suspend fun load(url: String): LoadResponse = operation {
        val target = Site.absolute(mainUrl, url)?.takeIf(Site::material)
            ?: throw ErrorLoadingException("Ekino TV: nieprawidłowy adres materiału.")
        val page = material(target)
        val info = try { Catalogue.material(page) } catch (_: IllegalArgumentException) {
            throw ErrorLoadingException("Ekino TV: nie znaleziono szczegółów. Sprawdź stronę / logowanie w ustawieniach dodatku.")
        }
        if (Site.path(page.url).startsWith("/serie/show/")) {
            if (info.episodes.isEmpty()) throw ErrorLoadingException("Ekino TV: serial nie udostępnia odcinków.")
            newTvSeriesLoadResponse(info.title, page.url, TvType.TvSeries, info.episodes.map { ep ->
                newEpisode(ep.url) { name = ep.name; season = ep.season; episode = ep.number }
            }) {
                posterUrl = info.poster
                backgroundPosterUrl = info.poster
                plot = info.plot
                year = info.year
                tags = info.tags
            }
        } else newMovieLoadResponse(info.title, page.url, TvType.Movie, page.url) {
            posterUrl = info.poster
            backgroundPosterUrl = info.poster
            plot = info.plot
            year = info.year
            tags = info.tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
                                   callback: (ExtractorLink) -> Unit): Boolean = operation {
        val target = Site.absolute(mainUrl, data)?.takeIf(Site::material)
            ?: throw ErrorLoadingException("Ekino TV: nieprawidłowy adres odtwarzania.")
        try {
            val page = material(target)
            val hosts = HostPlayback(HostHttp(browser.userAgent()), subtitleCallback)
            Playback(browser, hosts::resolve, browser.state::note).links(page) { media, player ->
                callback(newExtractorLink(name, listOf(player.host, player.language).filter(String::isNotBlank).joinToString(" · "),
                    media.url, if (media.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = media.referer; headers = media.headers; quality = media.quality
                })
            }
        } catch (error: SiteProblem) {
            throw ErrorLoadingException("${error.message}\n${browser.state.report()}")
        } finally { browser.finishPlayback() }
    }
}
