package pl.animegatecloudstream

import java.net.URI
import java.net.URLEncoder
import java.math.BigDecimal

/** Only the three GET contracts confirmed in the website. Transport owns authentication. */
internal class AnimeGateCatalogApi(private val get: (String) -> String) {
    fun catalog(query: String = ""): AnimeGateCatalogParser.Page = AnimeGateCatalogParser.parsePage(
        get("$BASE/anime/catalog-search?mode=preview&page=1&limit=8&search=${URLEncoder.encode(query, "UTF-8")}&sortBy=rating&sortOrder=desc"))

    fun details(url: String): AnimeGateCatalogParser.Details {
        val uri = URI(url)
        require(AnimeGateAuthPolicy.isSite(url) && uri.rawQuery == null && uri.rawFragment == null)
        val slug = uri.rawPath.removePrefix("/anime/")
        require(uri.rawPath.startsWith("/anime/") && slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]*)*")))
        return AnimeGateCatalogParser.parseDetails(get("$BASE/anime/slug/$slug"), slug)
    }

    fun episodes(animeId: String): List<AnimeGateEpisodeParser.Episode> {
        require(animeId.matches(Regex("[A-Za-z0-9_-]+")))
        return AnimeGateEpisodeParser.parse(get("$BASE/episodes/anime/$animeId?publicOnly=true"), animeId)
    }

    fun watchEpisode(url: String): AnimeGateEpisodeParser.Episode {
        val uri = URI(url)
        require(AnimeGateAuthPolicy.isSite(url) && uri.rawQuery == null && uri.rawFragment == null)
        val match = Regex("/watch/([a-z0-9]+(?:-[a-z0-9]*)*)/(-?[0-9]+(?:\\.[0-9]+)?)").matchEntire(uri.rawPath)
        require(match != null)
        val slug = match.groupValues[1]
        val number = BigDecimal(match.groupValues[2])
        val details = details("${AnimeGateAuthPolicy.BASE}/anime/$slug")
        return requireNotNull(episodes(details.anime.id).firstOrNull { it.number.compareTo(number) == 0 })
    }

    companion object {
        private const val BASE = "https://api.animegate.pl/api/v1"
        fun isGetUrl(url: String): Boolean = runCatching {
            val uri = URI(url)
            if (url.length > 4096 || !AnimeGateAuthPolicy.isApi(url) || uri.rawFragment != null) return false
            when {
                uri.rawPath == "/api/v1/anime/catalog-search" -> {
                    val keys = uri.rawQuery.orEmpty().split('&').map { it.substringBefore('=') }
                    keys.size == 6 && keys.toSet() == setOf("mode", "page", "limit", "search", "sortBy", "sortOrder")
                }
                uri.rawPath.matches(Regex("/api/v1/anime/slug/[a-z0-9]+(?:-[a-z0-9]*)*")) -> uri.rawQuery == null
                uri.rawPath.matches(Regex("/api/v1/episodes/anime/[A-Za-z0-9_-]+")) -> uri.rawQuery == "publicOnly=true"
                else -> false
            }
        }.getOrDefault(false)
    }
}
