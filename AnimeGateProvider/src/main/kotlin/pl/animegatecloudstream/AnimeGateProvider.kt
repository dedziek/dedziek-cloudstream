package pl.animegatecloudstream

import android.webkit.CookieManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.math.BigDecimal
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses the observed AnimeGate ticket contract and CloudStream's built-in host extractor. */
class AnimeGateProvider(private val session: AnimeGateSession) : MainAPI() {
    override var mainUrl = AnimeGateAuthPolicy.BASE
    override var name = "AnimeGate"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Anime)
    override val hasMainPage = true
    override val loadLinksTimeoutMs = 120_000L
    override val mainPage = mainPageOf("preview" to "Najwyżej oceniane — podgląd")
    private val catalog = AnimeGateCatalogApi(::get)
    private val playback = AnimeGatePlaybackApi(post = { url, body -> request(url, body) })
    private val media by lazy {
        AnimeGateMediaProbe(AnimeGateMediaHttp(app.baseClient)::read,
            app.defaultHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value.orEmpty())
    }

    private fun get(url: String): String = request(url)

    private fun request(url: String, body: String? = null): String {
        require(if (body == null) AnimeGateCatalogApi.isGetUrl(url)
            else AnimeGatePlaybackApi.isPostUrl(url))
        if (!session.hasSession()) throw ErrorLoadingException("AnimeGate: otwórz ustawienia dodatku, zaloguj się i wybierz „Zapisz”.")
        // Ask the browser cookie store for THIS exact destination. Never widen the saved
        // auth/me snapshot or forward it to catalogue requests or external image/video hosts.
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        val agent = session.agent()
        if (cookies.isBlank() || agent.isBlank()) throw ErrorLoadingException("AnimeGate: otwórz ustawienia dodatku i ponownie zapisz sesję.")
        require(cookies.length <= 65_536 && !cookies.contains('\r') && !cookies.contains('\n'))
        require(agent.length <= 4096 && !agent.contains('\r') && !agent.contains('\n'))
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status == 401 || status in 300..399) throw ErrorLoadingException("AnimeGate: sesja wygasła. Zaloguj się ponownie w ustawieniach dodatku.")
            if (status !in 200..299) throw ErrorLoadingException("AnimeGate: serwis zwrócił HTTP $status. Sprawdź dostęp w ustawieniach dodatku.")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1_048_576) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 1_048_576) throw ErrorLoadingException("AnimeGate: odpowiedź katalogu jest zbyt duża.")
            return String(bytes, Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    private fun AnimeGateCatalogParser.Anime.toResult(): SearchResponse =
        newAnimeSearchResponse(title, "$mainUrl/anime/$slug", TvType.Anime) { posterUrl = coverImage }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = withContext(Dispatchers.IO) {
        // Only the first preview page is verified. Do not advertise unverified pagination.
        val rows = if (page == 1) catalog.catalog().anime.map { it.toResult() } else emptyList()
        newHomePageResponse(request.name, rows, false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList = withContext(Dispatchers.IO) {
        val rows = if (query.isNotBlank() && page == 1) catalog.catalog(query).anime.map { it.toResult() } else emptyList()
        rows.toNewSearchResponseList(false)
    }

    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val details = catalog.details(url)
        val episodes = catalog.episodes(details.anime.id)
        newAnimeLoadResponse(details.anime.title, url, TvType.Anime) {
            posterUrl = details.anime.coverImage; year = details.anime.year
            plot = details.synopsis
            tags = details.anime.genres
            showStatus = when (details.anime.status) {
                "COMPLETED" -> ShowStatus.Completed
                "RELEASING" -> ShowStatus.Ongoing
                else -> null
            }
            addEpisodes(DubStatus.None, episodes.map { ep ->
                newEpisode("$mainUrl/watch/${details.anime.slug}/${ep.number.stripTrailingZeros().toPlainString()}") {
                    name = ep.displayName
                    episode = ep.number.takeIf { it.stripTrailingZeros().scale() <= 0 && it >= BigDecimal.ZERO && it <= BigDecimal(Int.MAX_VALUE) }?.toInt()
                }
            })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
                                   callback: (ExtractorLink) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val episode = catalog.watchEpisode(data)
            var found = false
            val failures = linkedSetOf<String>()
            for (version in episode.links.take(8)) {
                try {
                    val iframe = playback.resolve(episode.id, version.id)
                    // Only a public iframe address crosses this boundary, never AnimeGate cookies or tickets.
                    val extracted = mutableListOf<ExtractorLink>()
                    loadExtractor(iframe, "$mainUrl/", subtitleCallback) { extracted.add(it) }
                    for (link in extracted) {
                        val format = try { media.inspect(link.url, link.referer, link.headers) }
                        catch (error: AnimeGateMediaException) { failures.add(error.message.orEmpty()); continue }
                        val type = when (format) {
                            AnimeGateMediaProbe.Format.HLS -> ExtractorLinkType.M3U8
                            AnimeGateMediaProbe.Format.DASH -> ExtractorLinkType.DASH
                            AnimeGateMediaProbe.Format.VIDEO -> ExtractorLinkType.VIDEO
                        }
                        found = true
                        callback(newExtractorLink("AnimeGate", "${version.label} — ${link.name}", link.url, type) {
                            referer = link.referer; headers = link.headers; quality = link.quality
                            extractorData = link.extractorData
                        })
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: ErrorLoadingException) { throw error }
                catch (_: Exception) { /* Try remaining language versions, without logging response data. */ }
            }
            if (!found) throw ErrorLoadingException("AnimeGate: nie uzyskano wideo z dostępnych wersji odcinka. " +
                if (failures.isEmpty()) "Hosting nie udostępnił źródła. Sprawdź odtwarzacz na stronie."
                else failures.take(3).joinToString("; ") + ". Wybierz inną wersję odcinka lub sprawdź odtwarzacz na stronie.")
            true
        }
    }
}
