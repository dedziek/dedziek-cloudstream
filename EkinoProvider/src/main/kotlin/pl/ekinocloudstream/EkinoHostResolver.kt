package pl.ekinocloudstream

import java.net.URI
import kotlinx.coroutines.CancellationException

/** Built-in extractors first; one ordinary HTML read only when no extractor supports the host. */
internal class EkinoHostResolver(
    private val extractor: suspend (String, String) -> Dispatch,
    private val fetch: suspend (String, String) -> Response,
    private val emit: suspend (EkinoHostSources.Media) -> Unit,
    private val unpack: (String) -> String = { it }
) {
    enum class Dispatch { FOUND, EMPTY, UNSUPPORTED, ERROR }
    enum class Verification { NONE, ACCESS_DENIED, CLOUDFLARE, CAPTCHA }
    data class Response(val status: Int, val body: String = "", val location: String? = null)
    private val visited = mutableSetOf<String>()
    private val blocked = mutableSetOf<String>()
    private val trace = linkedSetOf<String>()
    private var requests = 0
    var verification = Verification.NONE
        private set

    fun verificationHint(): String = when (verification) {
        Verification.NONE -> ""
        Verification.ACCESS_DENIED -> EkinoMessages.ACCESS_DENIED
        Verification.CLOUDFLARE -> EkinoMessages.CLOUDFLARE
        Verification.CAPTCHA -> EkinoMessages.HOST_VERIFICATION
    }

    fun nextPlayer() { visited.clear() }

    fun report(): String = trace.take(8).joinToString("; ")
    private fun note(url: String, state: String) {
        val host = runCatching { URI(url).host }.getOrNull()?.take(100) ?: "nieznany"
        trace.add("$host:$state")
    }

    suspend fun resolve(url: String, referer: String, depth: Int = 0) {
        if (!EkinoHostSources.publicHttps(url) || depth > 3 || !visited.add(url)) return
        val host = URI(url).host
        if (host in blocked) return
        EkinoHostSources.media(url, referer)?.let { emit(it); note(url, "wideo"); return }
        val result = try { extractor(url, referer) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { Dispatch.ERROR }
        when (result) {
            Dispatch.FOUND -> { note(url, "ekstraktor OK"); return }
            Dispatch.EMPTY -> { note(url, "ekstraktor bez wideo"); return }
            Dispatch.ERROR -> { note(url, "błąd ekstraktora"); return }
            Dispatch.UNSUPPORTED -> note(url, "brak ekstraktora")
        }
        if (requests >= 6) { note(url, "limit odczytu"); return }
        requests++
        val page = try { fetch(url, referer) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { note(url, "błąd połączenia"); blocked.add(host); return }
        if (page.status in setOf(401, 403, 429)) {
            if (page.status == 403) verification = Verification.ACCESS_DENIED
            blocked.add(host); note(url, "HTTP ${page.status}"); return
        }
        if (page.status in setOf(301, 302, 303, 307, 308)) {
            val target = page.location?.let { EkinoParser.absolute(url, it) } ?: return
            resolve(target, url, depth + 1)
            return
        }
        if (page.status !in 200..299) { note(url, "HTTP ${page.status}"); return }
        val doc = EkinoParser.document(page.body, url)
        val cloudflare = EkinoParser.isChallenge(doc)
        if (cloudflare || EkinoParser.isPlayerVerification(doc)) {
            verification = if (cloudflare) Verification.CLOUDFLARE else Verification.CAPTCHA
            blocked.add(host); note(url, "weryfikacja wymagana"); return
        }
        val expanded = runCatching { unpack(page.body) }.getOrDefault("")
        val parsed = EkinoHostSources.parse(page.body, url, expanded)
        for (media in parsed.media) emit(media)
        if (parsed.media.isNotEmpty()) { note(url, "źródło HTML"); return }
        note(url, if (parsed.frames.isEmpty()) "brak źródła HTML" else "ramka pośrednia")
        for (frame in parsed.frames) resolve(frame, url, depth + 1)
    }
}
