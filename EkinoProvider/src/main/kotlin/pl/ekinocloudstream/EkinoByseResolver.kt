package pl.ekinocloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.decodeBase64
import org.jsoup.nodes.Document

/** The public Byse embed's bounded initialization, without Ekino credentials or mirror changes. */
internal class EkinoByseResolver(
    private val post: suspend (url: String, referer: String, body: String, token: String?) -> EkinoHostResolver.Response,
    private val work: suspend (String, Int) -> String? = { nonce, difficulty ->
        withContext(Dispatchers.Default) { withTimeoutOrNull(15_000) { EkinoByseWork.solve(nonce, difficulty) } }
    }
) {
    enum class Stage(val label: String) { INIT("inicjalizacja"), VERIFY("potwierdzenie"), PLAYBACK("odczyt źródeł"), DECODE("format źródeł"), WORK("limit obliczeń") }
    data class Result(val media: List<EkinoHostSources.Media> = emptyList(), val stage: Stage = Stage.INIT,
                      val status: Int? = null,
                      val verification: EkinoHostResolver.Verification = EkinoHostResolver.Verification.NONE) {
        fun report(): String = when {
            media.isNotEmpty() -> "Byse API OK"
            verification == EkinoHostResolver.Verification.CLOUDFLARE -> "Byse: wymagana weryfikacja Cloudflare"
            verification != EkinoHostResolver.Verification.NONE -> "Byse: wymagana ręczna CAPTCHA"
            else -> "Byse: ${stage.label}" + (status?.let { " HTTP $it" } ?: " bez źródeł")
        }
    }
    private class VerificationRequired(val kind: EkinoHostResolver.Verification) : Exception()

    suspend fun resolve(embed: String): Result {
        val endpoint = endpoint(embed) ?: return Result()
        var stage = Stage.INIT
        var status: Int? = null
        return try {
            suspend fun call(path: String, body: String, token: String? = null): JsonNode? {
                val response = post("$endpoint/$path", embed, body, token)
                status = response.status
                if (response.status != 200 || response.body.length > 1_048_576) return null
                val doc = EkinoParser.document(response.body)
                if (EkinoParser.isChallenge(doc)) throw VerificationRequired(EkinoHostResolver.Verification.CLOUDFLARE)
                if (EkinoParser.isPlayerVerification(doc)) throw VerificationRequired(EkinoHostResolver.Verification.CAPTCHA)
                return mapper.readTree(response.body)?.takeIf { it.isObject }
            }
            val challenge = call("captcha", "{}") ?: return Result(stage = stage, status = status)
            val nonce = challenge.path("pow_nonce").takeIf { it.isTextual }?.asText() ?: return Result(stage = stage)
            val difficulty = challenge.path("pow_difficulty").takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()
                ?: return Result(stage = stage)
            val challengeToken = challenge.path("pow_token").takeIf { it.isTextual }?.asText()?.takeIf { it.length in 1..8192 }
                ?: return Result(stage = stage)
            stage = Stage.WORK
            if (nonce.length !in 1..256 || nonce.any { it.code !in 0..127 } || difficulty !in 0..20) return Result(stage = stage)
            val solution = work(nonce, difficulty)?.takeIf { it.matches(Regex("[0-9]{1,10}")) } ?: return Result(stage = stage)
            stage = Stage.VERIFY
            val verification = call("captcha/verify", mapper.writeValueAsString(mapOf("pow_token" to challengeToken, "solution" to solution)))
                ?: return Result(stage = stage, status = status)
            if (verification.path("status").asText() != "ok") return Result(stage = stage)
            val token = verification.path("token").takeIf { it.isTextual }?.asText()?.takeIf { it.length in 1..8192 && !it.contains('\r') && !it.contains('\n') }
                ?: return Result(stage = stage)
            stage = Stage.PLAYBACK
            // This is the public embed API's empty fingerprint, not a forged browser identity.
            val playback = call("playback", "{\"fingerprint\":{\"token\":\"\",\"viewer_id\":\"\",\"device_id\":\"\"}}", token)
                ?: return Result(stage = stage, status = status)
            stage = Stage.DECODE
            Result(media = decode(playback, embed), stage = stage)
        } catch (error: CancellationException) { throw error }
        catch (error: VerificationRequired) { Result(stage = stage, status = status, verification = error.kind) }
        catch (_: Exception) { Result(stage = stage) }
    }

    companion object {
        private val mapper = ObjectMapper()
        fun matches(doc: Document, url: String): Boolean = endpoint(url) != null &&
            doc.title().trim().equals("Byse Frontend", true) && doc.selectFirst("div#root") != null

        private fun endpoint(embed: String): String? = runCatching {
            if (!EkinoHostSources.publicHttps(embed)) return null
            val uri = URI(embed)
            val code = Regex("/e/([a-zA-Z0-9_-]{1,128})/?").matchEntire(uri.path)?.groupValues?.get(1) ?: return null
            "https://${uri.rawAuthority}/api/videos/$code/embed"
        }.getOrNull()

        /** Authenticated transport payload supplied to every free embed viewer; no DRM keys. */
        fun decode(response: JsonNode, embed: String): List<EkinoHostSources.Media> {
            require(endpoint(embed) != null)
            val data = response.path("playback")
            val algorithm = data.path("algorithm").asText()
            require(algorithm in setOf("AES-GCM", "AES-256-GCM"))
            val parts = data.path("key_parts")
            require(parts.isArray && parts.size() in 1..64)
            val version = data.path("version").asText().toIntOrNull()
            val indices = if (version != null && version in 1..20 && version <= parts.size() && 31 - version <= parts.size())
                listOf(version - 1, 30 - version) else (0 until parts.size()).toList()
            val key = indices.map { base64(parts[it].asText()) }.fold(byteArrayOf()) { a, b -> a + b }
            require(key.size in setOf(16, 24, 32) && (algorithm != "AES-256-GCM" || key.size == 32))
            val iv = base64(data.path("iv").asText())
            require(iv.size == 12)
            val payload = base64(data.path("payload").asText())
            require(payload.size in 16..1_048_576)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val decoded = mapper.readTree(cipher.doFinal(payload))
            val uri = URI(embed)
            val origin = "${uri.scheme}://${uri.rawAuthority}"
            return decoded.path("sources").takeIf { it.isArray }?.take(12)?.mapNotNull { source ->
                val raw = source.path("url").takeIf { it.isTextual }?.asText() ?: return@mapNotNull null
                EkinoHostSources.media(raw, embed, source.path("mime_type").asText())
                    ?.copy(headers = mapOf("Origin" to origin))
            }?.distinctBy { it.url }.orEmpty()
        }

        private fun base64(value: String): ByteArray {
            require(value.length in 1..1_400_000 && value.matches(Regex("[a-zA-Z0-9_+/=-]+")))
            return requireNotNull(value.decodeBase64()).toByteArray()
        }
    }
}
