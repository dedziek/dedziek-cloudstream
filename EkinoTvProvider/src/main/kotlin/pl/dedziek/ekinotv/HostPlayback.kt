package pl.dedziek.ekinotv

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class Media(val url: String, val referer: String, val hls: Boolean,
                          val quality: Int = 0, val headers: Map<String, String> = emptyMap())
internal data class HostResult(val media: List<Media> = emptyList(), val message: String = "brak wideo", val blocked: Boolean = false)
internal data class HostResponse(val status: Int, val body: String, val location: String? = null)

/** An operation-local hosting client. It cannot receive or read the site's WebView cookies. */
internal class HostHttp(private val agent: String) {
    private val cookies = java.util.Collections.synchronizedList(mutableListOf<Cookie>())
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .followSslRedirects(false).callTimeout(10, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(this@HostHttp.cookies) {
                    this@HostHttp.cookies.removeAll { old -> old.expiresAt < System.currentTimeMillis() || cookies.any {
                        it.name == old.name && it.domain == old.domain && it.path == old.path
                    } }
                    this@HostHttp.cookies.addAll(cookies)
                }
            }
            override fun loadForRequest(url: HttpUrl) = synchronized(cookies) {
                cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
            }
        }).build()
    private var requests = 0

    fun headers(url: String): Map<String, String> {
        val result = mutableMapOf("User-Agent" to agent)
        val parsed = runCatching { Request.Builder().url(url).build().url }.getOrNull() ?: return result
        synchronized(cookies) {
            cookies.filter { it.matches(parsed) && it.expiresAt > System.currentTimeMillis() }
                .joinToString("; ") { "${it.name}=${it.value}" }.takeIf(String::isNotBlank)?.let { result["Cookie"] = it }
        }
        return result
    }

    suspend fun request(url: String, referer: String, json: String? = null, token: String? = null): HostResponse {
        require(Site.absolute(url, url) != null && !Site.official(url) && !Site.bridge(url) && !Site.challengeHost(url))
        require(++requests <= 20) { "Przekroczono limit żądań do hostingów" }
        val request = Request.Builder().url(url).header("User-Agent", agent).header("Referer", referer)
        if (json != null) {
            require(Site.origin(url) == Site.origin(referer))
            require(Site.path(url).matches(Regex("/api/videos/[A-Za-z0-9_-]{1,128}/embed/(captcha|captcha/verify|playback)")))
            request.header("Origin", Site.origin(referer)).header("Accept", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
            token?.let { require(it.length in 1..8192); request.header("X-Captcha-Token", it) }
        }
        val call = client.newCall(request.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val bytes = it.body.byteStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (output.size() <= 1_048_576) {
                                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                if (output.size() > 1_048_576) throw IOException("Zbyt duża odpowiedź hostingu")
                                output.toByteArray()
                            }
                            HostResponse(it.code, bytes.toString(Charsets.UTF_8), it.header("Location"))
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
}

/** Implements the ordinary public Byse player protocol, independently of the Ekino browser. */
internal class BysePlayer(
    private val call: suspend (String, String, String, String?) -> HostResponse,
    private val solve: suspend (String, Int) -> String? = { nonce, bits ->
        withContext(Dispatchers.Default) { withTimeoutOrNull(15_000) { PlayerWork.answer(nonce, bits) } }
    }
) {
    suspend fun sources(embed: String): HostResult {
        val id = Regex("/e/([A-Za-z0-9_-]{1,128})/?").matchEntire(Site.path(embed))?.groupValues?.get(1)
            ?: return HostResult(message = "Byse: nieznany adres odtwarzacza")
        val endpoint = "${Site.origin(embed)}/api/videos/$id/embed"
        var step = "inicjalizacja"
        return try {
            suspend fun post(route: String, data: Any, token: String? = null): JsonNode {
                val response = call("$endpoint/$route", embed, mapper.writeValueAsString(data), token)
                if (response.status in setOf(401, 403, 429)) throw HostingDenied(response.status)
                check(response.status == 200) { "HTTP ${response.status}" }
                if (response.body.trimStart().startsWith("<")) throw HostingGate()
                return mapper.readTree(response.body)
            }
            val task = post("captcha", emptyMap<String, String>())
            val nonce = task.path("pow_nonce").asText()
            val bits = task.path("pow_difficulty").takeIf { it.isIntegralNumber }?.asInt() ?: -1
            val ticket = task.path("pow_token").asText()
            require(nonce.length in 1..256 && nonce.all { it.code in 0..127 } && bits in 0..20 && ticket.length in 1..8192)
            step = "obliczenia publicznego odtwarzacza"
            val answer = solve(nonce, bits) ?: return HostResult(message = "Byse: przekroczono limit obliczeń")
            step = "potwierdzenie"
            val verified = post("captcha/verify", mapOf("pow_token" to ticket, "solution" to answer))
            check(verified.path("status").asText() == "ok")
            val token = verified.path("token").asText()
            require(token.length in 1..8192 && '\n' !in token && '\r' !in token)
            step = "odczyt źródeł"
            val response = post("playback", mapOf("fingerprint" to mapOf("token" to "", "viewer_id" to "", "device_id" to "")), token)
            val media = decode(response, embed)
            HostResult(media, if (media.isEmpty()) "Byse: brak źródeł" else "Byse: źródła odczytane")
        } catch (error: CancellationException) { throw error }
        catch (error: HostingDenied) { HostResult(message = "Byse: HTTP ${error.status}; dalsze próby zatrzymano", blocked = true) }
        catch (_: HostingGate) { HostResult(message = "Byse wymaga weryfikacji w przeglądarce", blocked = true) }
        catch (_: Exception) { HostResult(message = "Byse: nie powiodło się — $step") }
    }

    private class HostingDenied(val status: Int) : Exception()
    private class HostingGate : Exception()
    companion object {
        private val mapper = ObjectMapper()
        fun decode(response: JsonNode, embed: String): List<Media> {
            val envelope = response.path("playback")
            val algorithm = envelope.path("algorithm").asText()
            require(algorithm in setOf("AES-GCM", "AES-256-GCM"))
            fun bytes(value: String): ByteArray {
                require(value.length in 1..1_400_000)
                return requireNotNull(value.decodeBase64()).toByteArray()
            }
            val parts = envelope.path("key_parts")
            require(parts.isArray && parts.size() in 1..64)
            val version = envelope.path("version").asText().toIntOrNull()
            val indices = if (version != null) {
                require(version in 1..20 && version <= parts.size() && 31 - version <= parts.size())
                listOf(version - 1, 30 - version)
            } else (0 until parts.size()).toList()
            val key = indices.fold(byteArrayOf()) { key, index -> key + bytes(parts[index].asText()) }
            require(key.size in setOf(16, 24, 32) && (algorithm != "AES-256-GCM" || key.size == 32))
            val iv = bytes(envelope.path("iv").asText()); require(iv.size == 12)
            val encrypted = bytes(envelope.path("payload").asText()); require(encrypted.size in 16..1_048_576)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val payload = mapper.readTree(cipher.doFinal(encrypted))
            return payload.path("sources").take(12).mapNotNull { source ->
                val url = source.path("url").asText()
                val type = source.path("mime_type").asText()
                val path = Site.path(url).lowercase()
                if (Site.absolute(url, url) == null || Site.official(url) || Site.bridge(url)) return@mapNotNull null
                val hls = path.endsWith(".m3u8") || type.contains("mpegurl", true)
                if (!hls && !path.endsWith(".mp4") && !type.equals("video/mp4", true)) return@mapNotNull null
                Media(url, embed, hls, source.path("height").asInt(0), mapOf("Origin" to Site.origin(embed)))
            }.distinctBy { it.url }
        }
    }
}

internal class HostPlayback(private val http: HostHttp, private val subtitles: (SubtitleFile) -> Unit) {
    suspend fun resolve(url: String, referer: String): HostResult {
        var target = url
        val visited = mutableSetOf<String>()
        repeat(4) {
            if (!visited.add(target)) return HostResult(message = "pętla przekierowania hostingu")
            val direct = direct(target, referer)
            if (direct != null) return HostResult(listOf(direct.copy(headers = http.headers(target))))
            val response = http.request(target, referer)
            if (response.status in setOf(401, 403, 429)) return HostResult(message = "hosting: HTTP ${response.status}", blocked = true)
            if (response.status in 300..399) {
                target = response.location?.let { Site.absolute(target, it) } ?: return HostResult(message = "niepoprawne przekierowanie")
                if (Site.official(target) || Site.bridge(target)) return HostResult(message = "hosting przekierował do serwisu")
            } else {
                if (response.status !in 200..299) return HostResult(message = "hosting: HTTP ${response.status}")
                val doc = Jsoup.parse(response.body, target)
                if (BrowserSnapshot(WebPage(target, response.body)).gate() != null)
                    return HostResult(message = "hosting wymaga ręcznej weryfikacji", blocked = true)
                if (doc.title().trim().equals("Byse Frontend", true) && doc.selectFirst("div#root") != null) {
                    val result = BysePlayer(http::request).sources(target)
                    return result.copy(media = result.media.map { it.copy(headers = http.headers(it.url) + it.headers) })
                }
                val links = mutableListOf<Media>()
                val host = URI(target).host
                val unsupportedUpzone = host.split('.').any { it.equals("upzone", true) } &&
                    extractorApis.none { runCatching { URI(it.mainUrl).host.equals(host, true) }.getOrDefault(false) }
                if (!unsupportedUpzone) loadExtractor(target, referer, subtitles) { link ->
                    links.add(Media(link.url, link.referer, link.type.name == "M3U8", link.quality, link.headers))
                }
                if (links.isNotEmpty()) return HostResult(links)
                val script = runCatching { getAndUnpack(response.body) }.getOrDefault(response.body)
                val candidates = doc.select("video[src], video source[src]").map { it.attr("src") } +
                    Regex("""(?:file|src)\s*[:=]\s*['"](https?[^'"\s]+)['"]""").findAll(script).map { it.groupValues[1] }.toList()
                val media = candidates.mapNotNull { raw -> Site.absolute(target, raw)?.let { direct(it, target) } }
                    .map { it.copy(headers = http.headers(it.url)) }.distinctBy { it.url }
                return HostResult(media, if (media.isEmpty()) "hosting nie udostępnił źródła" else "źródło HTML")
            }
        }
        return HostResult(message = "za dużo przekierowań hostingu")
    }

    private fun direct(url: String, referer: String): Media? {
        if (Site.absolute(url, url) == null || Site.official(url) || Site.bridge(url)) return null
        val path = Site.path(url).lowercase()
        return when { path.endsWith(".m3u8") -> Media(url, referer, true); path.endsWith(".mp4") -> Media(url, referer, false); else -> null }
    }
}
