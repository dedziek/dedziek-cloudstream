package pl.animegatecloudstream

import android.webkit.CookieManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URI
import java.math.BigDecimal
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Catalogue preview is usable; playback is explicitly unavailable in this version. */
class AnimeGateProvider(private val session: AnimeGateSession) : MainAPI() {
    override var mainUrl = AnimeGateAuthPolicy.BASE
    override var name = "AnimeGate"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Anime)
    override val hasMainPage = true
    override val mainPage = mainPageOf("preview" to "Najwyżej oceniane — podgląd")
    private val catalog = AnimeGateCatalogApi(::get)

    private fun get(url: String): String {
        require(AnimeGateAuthPolicy.isApi(url) && AnimeGateCatalogContract.eligible(url))
        if (!session.hasSession()) throw ErrorLoadingException("AnimeGate: otwórz ustawienia dodatku, zaloguj się i wybierz „Zapisz i sprawdź”.")
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
            val status = connection.responseCode
            if (status == 401 || status in 300..399) throw ErrorLoadingException("AnimeGate: sesja wygasła. Zaloguj się ponownie w ustawieniach dodatku.")
            if (status != 200) throw ErrorLoadingException("AnimeGate: serwis zwrócił HTTP $status. Sprawdź dostęp w ustawieniach dodatku.")
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
            plot = "Odtwarzanie w CloudStream nie jest jeszcze dostępne.\n\n" + details.synopsis.orEmpty()
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
        throw ErrorLoadingException("AnimeGate: katalog i odcinki są dostępne, ale odtwarzanie w CloudStream nie jest jeszcze obsługiwane.")
    }
}
