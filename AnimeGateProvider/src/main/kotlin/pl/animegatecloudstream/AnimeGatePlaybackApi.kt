package pl.animegatecloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI

/** Tickets exist only in this call; never persist, cache, or include them in logs. */
internal class AnimeGatePlaybackApi(private val post: (String, String) -> String,
                                    private val clock: () -> Long = System::currentTimeMillis) {
    fun resolve(episodeId: String, linkId: String): String {
        require(identifier(episodeId) && identifier(linkId))
        val mapper = ObjectMapper()
        val started = clock()
        val ticketData = data(post("$BASE/$episodeId/playback", mapper.writeValueAsString(mapOf("linkId" to linkId))))
        val ticket = ticketData.path("ticket").takeIf { it.isTextual }?.asText()
        require(!ticket.isNullOrBlank() && ticket.length <= 4096 && ticket.none { it.isISOControl() })
        val expiry = ticketData.path("expiresIn")
        require(expiry.isIntegralNumber && expiry.canConvertToInt() && expiry.intValue() in 1..3600)
        val elapsed = clock() - started
        require(elapsed >= 0 && elapsed < expiry.intValue() * 1000L) { "Bilet odtwarzania wygasł." }
        val resolved = data(post("$BASE/$episodeId/playback/resolve", mapper.writeValueAsString(mapOf("ticket" to ticket))))
        val iframe = resolved.path("iframeUrl").takeIf { it.isTextual }?.asText()
        require(iframe != null && isPlayerUrl(iframe)) { "Nieobsługiwany adres odtwarzacza AnimeGate." }
        return iframe
    }

    private fun data(body: String): JsonNode {
        val json = ObjectMapper().readTree(body)
        require(json != null && json.isObject && json.path("success").isBoolean && json.path("success").asBoolean())
        require(json.path("code").isIntegralNumber && json.path("code").asInt() in 200..299 && !json.hasNonNull("error"))
        require(json.path("data").isObject)
        return json.path("data")
    }

    companion object {
        private const val BASE = "https://api.animegate.pl/api/v1/episodes"
        private fun identifier(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))
        fun isPostUrl(url: String): Boolean = runCatching {
            val uri = URI(url)
            AnimeGateAuthPolicy.isApi(url) && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.matches(Regex("/api/v1/episodes/[A-Za-z0-9_-]{1,128}/playback(?:/resolve)?"))
        }.getOrDefault(false)
        fun isPlayerUrl(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "playmogo.com" && uri.userInfo == null &&
                uri.port in setOf(-1, 443) && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.matches(Regex("/e/[A-Za-z0-9]+"))
        }.getOrDefault(false)
    }
}
