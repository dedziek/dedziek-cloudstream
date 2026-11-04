package pl.animegatecloudstream

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal

/** The episode-list envelope and fields were confirmed by the v6 device report. */
internal object AnimeGateEpisodeParser {
    class Link(val id: String, val label: String)
    class Episode(val id: String, val animeId: String, val number: BigDecimal, val title: String?, val links: List<Link>) {
        val linkIds: List<String> get() = links.map { it.id }
        val displayName: String get() = title ?: "Odcinek ${number.stripTrailingZeros().toPlainString()}"
    }

    fun parse(body: String, expectedAnimeId: String? = null): List<Episode> {
        val json = ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body)
        require(json != null && json.isObject && json.path("data").isArray)
        require(!(json.path("success").isBoolean && !json.path("success").asBoolean()) && !json.hasNonNull("error"))
        return json.path("data").mapNotNull { node ->
            val id = node.path("id").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            val animeId = node.path("animeId").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            val number = node.path("number").takeIf { it.isNumber }?.decimalValue()
            if (id == null || animeId == null || number == null || (expectedAnimeId != null && animeId != expectedAnimeId)) return@mapNotNull null
            val title = node.path("title").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            val links = node.path("links").takeIf { it.isArray }?.mapNotNull {
                val linkId = it.path("id").takeIf { value -> value.isTextual }?.asText()?.takeIf(String::isNotBlank)
                val label = it.path("label").takeIf { value -> value.isTextual }?.asText()?.trim()?.takeIf(String::isNotBlank) ?: "Wersja"
                linkId?.let { value -> Link(value, label) }
            }?.distinctBy { it.id }.orEmpty()
            Episode(id, animeId, number, title, links)
        }.distinctBy { it.id }.sortedWith(compareBy<Episode> { it.number }.thenBy { it.id })
    }
}
