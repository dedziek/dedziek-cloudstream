package pl.animewatchcloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CancellationException

class AnimeWatchProvider(private val verification: AnimeWatchPlaybackSession = AnimeWatchPlaybackSession()) : MainAPI() {
    private data class ByseDetails(@param:JsonProperty("embed_frame_url") val frameUrl: String? = null)
    override var mainUrl = "https://animewatch.pl"
    override var name = "AnimeWatch"
    override var lang = "pl"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override val hasMainPage = true
    override val mainPage = mainPageOf("update" to "Ostatnio zaktualizowane", "title" to "Lista anime")

    private suspend fun sitePage(url: String): Document {
        if (!AnimeWatchParser.isSiteUrl(url)) throw ErrorLoadingException("AnimeWatch: nieprawidłowy adres strony.")
        val response = app.get(url, headers = mapOf("Accept-Language" to "pl,en;q=0.7"))
        if (response.code == 403 || response.code == 429) {
            throw ErrorLoadingException("AnimeWatch: strona odrzuciła połączenie (HTTP ${response.code}). Sprawdź dostęp w przeglądarce.")
        }
        if (response.code !in 200..299) throw ErrorLoadingException("AnimeWatch: błąd HTTP ${response.code}.")
        val doc = AnimeWatchParser.document(response.text, url)
        if (AnimeWatchParser.isChallenge(doc) || doc.selectFirst("#challenge-running") != null) {
            throw ErrorLoadingException("AnimeWatch: strona wymaga weryfikacji w przeglądarce.")
        }
        return doc
    }

    private fun contentType(type: String?): TvType = when (type?.lowercase()) {
        "movie", "film" -> TvType.AnimeMovie
        "ova", "ona", "special" -> TvType.OVA
        else -> TvType.Anime
    }

    private fun AnimeWatchParser.Card.toResult(): SearchResponse =
        newAnimeSearchResponse(title, url, contentType(type)) {
            posterUrl = poster
            addDubStatus(DubStatus.Dubbed)
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/anime/?page=$page&order=${URLEncoder.encode(request.data, "UTF-8")}"
        val doc = sitePage(url)
        val cards = AnimeWatchParser.cards(doc, url)
        if (page == 1 && cards.isEmpty()) throw ErrorLoadingException("AnimeWatch: nie znaleziono katalogu; strona mogła zmienić układ.")
        return newHomePageResponse(request.name, cards.map { it.toResult() }, AnimeWatchParser.nextPage(doc, url) != null)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (query.isBlank()) return emptyList<SearchResponse>().toNewSearchResponseList(false)
        val prefix = if (page > 1) "/page/$page/" else "/"
        val url = "$mainUrl$prefix?s=${URLEncoder.encode(query, "UTF-8")}"
        val doc = sitePage(url)
        return AnimeWatchParser.cards(doc, url).map { it.toResult() }
            .toNewSearchResponseList(AnimeWatchParser.nextPage(doc, url) != null)
    }

    override suspend fun load(url: String): LoadResponse {
        val details = AnimeWatchParser.details(sitePage(url), url)
        if (details.episodes.isEmpty()) throw ErrorLoadingException("AnimeWatch: dla tego tytułu nie ma dostępnych odcinków.")
        val type = contentType(details.type)
        return if (type == TvType.AnimeMovie) {
            newMovieLoadResponse(details.title, url, type, details.episodes.first().url) {
                posterUrl = details.poster; plot = details.plot; year = details.year
                tags = details.genres; recommendations = details.recommendations.map { it.toResult() }
            }
        } else {
            newAnimeLoadResponse(details.title, url, type) {
                posterUrl = details.poster; plot = details.plot; year = details.year
                tags = details.genres; recommendations = details.recommendations.map { it.toResult() }
                showStatus = when {
                    details.status?.contains("Zakończone", ignoreCase = true) == true -> ShowStatus.Completed
                    details.status?.contains("Emitowane", ignoreCase = true) == true -> ShowStatus.Ongoing
                    else -> null
                }
                addEpisodes(DubStatus.Dubbed, details.episodes.map { ep ->
                    newEpisode(ep.url) {
                        name = if (ep.episode != null && ep.episode % 1.0 != 0.0) "${ep.title} (${ep.episode})" else ep.title
                        // Fractional/special numbers remain in the visible title.
                        episode = ep.episode?.takeIf { it % 1.0 == 0.0 }?.toInt()
                    }
                })
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean,
                                   subtitleCallback: (SubtitleFile) -> Unit,
                                   callback: (ExtractorLink) -> Unit): Boolean {
        verification.take(data)?.let { stream -> emitStream(stream, callback); return true }
        val targets = AnimeWatchPlayers.targets(sitePage(data), data)
        if (targets.isEmpty()) throw ErrorLoadingException("AnimeWatch: brak odtwarzacza na stronie odcinka.")
        var found = false
        var playerError: String? = null
        var verificationError: String? = null
        for (target in targets.take(12)) {
            try {
                loadExtractor(target, "$mainUrl/", subtitleCallback) { link ->
                    found = true
                    callback(link)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A broken mirror should not prevent attempts with the remaining mirrors.
            }
        }
        if (!found) {
            for (target in targets.filter(AnimeWatchPlayers::isByse).take(3)) {
                val frame = byseFrame(target)
                // Keep the manual recovery action available even when the frame never
                // initialises far enough to render a recognisable CAPTCHA gate.
                verification.remember(AnimeWatchPlaybackSession.Request(data, target, frame))
                try {
                    // The site's own JavaScript handles the current playback API and session.
                    // Read its ready playlist in the background rather than asking for a tap.
                    // The /e/ parent must initialise its cross-origin player iframe.
                    val stream = AnimeWatchBrowser.resolve(target, data, frame) ?: continue
                    emitStream(stream, callback)
                    found = true
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AnimeWatchBrowser.VerificationRequired) {
                    verificationError = error.message
                } catch (error: Exception) {
                    playerError = error.message
                    // Continue with another mirror if this host or WebView cannot initialise.
                }
            }
        }
        if (!found) throw ErrorLoadingException((verificationError ?: playerError)?.let { "AnimeWatch: $it" }
            ?: "AnimeWatch: nie uzyskano wideo. Sprawdź odcinek na stronie; hosting może być chwilowo niedostępny.")
        return true
    }

    private suspend fun emitStream(stream: AnimeWatchPlayback.Stream, callback: (ExtractorLink) -> Unit) {
        val kind = if (stream.isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        callback(newExtractorLink("AnimeWatch", "Byse — AnimeWatch", stream.url, kind) {
            referer = stream.referer
            headers = stream.headers
        })
    }

    private suspend fun byseFrame(target: String): String? {
        return try {
            val uri = URI(target)
            val code = uri.path.trim('/').split('/').getOrNull(1) ?: return null
            if (!code.matches(Regex("[A-Za-z0-9]+"))) return null
            val response = app.get("https://${uri.host}/api/videos/$code/embed/details", referer = "$mainUrl/")
            if (response.code !in 200..299) return null
            tryParseJson<ByseDetails>(response.text)?.frameUrl?.takeIf { AnimeWatchPlayback.publicHttps(it) != null }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
