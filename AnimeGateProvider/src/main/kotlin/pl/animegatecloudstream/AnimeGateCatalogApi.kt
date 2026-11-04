package pl.animegatecloudstream

import java.net.URI
import java.net.URLEncoder

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

    companion object { private const val BASE = "https://api.animegate.pl/api/v1" }
}
