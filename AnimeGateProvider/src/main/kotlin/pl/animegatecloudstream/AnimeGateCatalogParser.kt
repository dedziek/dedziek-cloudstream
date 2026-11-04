package pl.animegatecloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI

/** Read-only catalogue contracts observed in the signed-in website on 2026-11-04. */
internal object AnimeGateCatalogParser {
    data class Anime(
        val id: String,
        val slug: String,
        val title: String,
        val year: Int?,
        val status: String?,
        val coverImage: String?,
        val episodeCount: Int?,
        val totalEpisodes: Int?,
        /** AnimeGate uses a 0–100 score, not a 0–10 score. */
        val rating: Int?,
        val genres: List<String>,
    )
    data class Page(val anime: List<Anime>, val total: Int, val page: Int, val limit: Int,
                    val totalPages: Int, val hasNextPage: Boolean, val hasPrevPage: Boolean)
    data class Details(val anime: Anime, val synopsis: String?, val bannerImage: String?,
                       val durationMinutes: Int?, val ageRating: String?, val season: String?,
                       val anilistId: Int?, val themes: List<String>)

    fun parsePage(body: String): Page {
        val data = envelope(body)
        require(data.isObject && data.path("data").isArray && data.path("meta").isObject)
        val meta = data.path("meta")
        val total = requireNotNull(meta.integer("total", 0..Int.MAX_VALUE))
        val page = requireNotNull(meta.integer("page", 1..Int.MAX_VALUE))
        val limit = requireNotNull(meta.integer("limit", 1..Int.MAX_VALUE))
        val totalPages = requireNotNull(meta.integer("totalPages", 0..Int.MAX_VALUE))
        require(meta.path("hasNextPage").isBoolean && meta.path("hasPrevPage").isBoolean)
        val anime = data.path("data").mapNotNull(::anime).distinctBy { it.id }
        return Page(anime, total, page, limit, totalPages, meta.path("hasNextPage").asBoolean(),
            meta.path("hasPrevPage").asBoolean())
    }

    fun parseDetails(body: String, expectedSlug: String? = null): Details {
        val data = envelope(body)
        val anime = requireNotNull(anime(data))
        require(expectedSlug == null || anime.slug == expectedSlug)
        // Observed duration is a string ("24"); no conversion of missing values to zero.
        val duration = data.text("duration")?.toIntOrNull()?.takeIf { it > 0 }
        return Details(anime, data.text("synopsis"), image(data.text("bannerImage")), duration,
            data.text("ageRating"), data.text("season"), data.integer("anilistId", 1..Int.MAX_VALUE),
            names(data.path("themes")))
    }

    private fun envelope(body: String): JsonNode {
        val json = ObjectMapper().readTree(body)
        require(json != null && json.isObject && json.hasNonNull("data"))
        require(!(json.path("success").isBoolean && !json.path("success").asBoolean()) && !json.hasNonNull("error"))
        require(!(json.path("code").isIntegralNumber && json.path("code").asLong() >= 400))
        return json.path("data")
    }

    private fun anime(node: JsonNode): Anime? {
        if (!node.isObject) return null
        val id = node.text("id") ?: return null
        val slug = node.text("slug") ?: return null
        val title = node.text("title") ?: return null
        // Slugs stay identifiers. They cannot inject another path, query, or origin later.
        if (!slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]*)*"))) return null
        return Anime(id, slug, title, node.integer("year", 1..9999), node.text("status"),
            image(node.text("coverImage")), node.integer("episodeCount", 0..Int.MAX_VALUE),
            node.integer("totalEpisodes", 0..Int.MAX_VALUE), node.integer("effectiveRating", 0..100),
            names(node.path("genres")))
    }

    private fun names(node: JsonNode): List<String> = if (node.isArray)
        node.mapNotNull { it.text("name") }.distinct() else emptyList()

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isTextual }
        ?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonNode.integer(field: String, range: IntRange): Int? = path(field)
        .takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it in range }

    private fun image(value: String?): String? = value?.takeIf {
        runCatching { URI(it).let { uri -> uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.port in setOf(-1, 443) && uri.rawFragment == null } }.getOrDefault(false)
    }
}
