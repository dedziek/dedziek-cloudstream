package pl.ogladajanimecloudstream

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.net.URI

internal class OgladajAnimeSite(
    private val fetch: suspend (String, Map<String, String>?, Map<String, String>) -> Response
) {
    data class Page(val url: String, val text: String)
    class Problem(message: String) : Exception("OglądajAnime: $message")

    private val lock = Mutex()
    private val cookies = mutableMapOf<String, Cookie>()

    private fun cookie(url: String): Cookie? {
        val target = url.toHttpUrl()
        return cookies[target.host]?.takeIf { it.expiresAt > System.currentTimeMillis() && it.matches(target) }
    }

    private suspend fun bootstrap(url: String): String {
        val target = url.toHttpUrl()
        val root = target.newBuilder().encodedPath("/").query(null).build()
        if (target == root) return url
        val home = exchange(root.toString(), null)
        return home.url.toHttpUrl().newBuilder().encodedPath(target.encodedPath).encodedQuery(target.encodedQuery).build().toString()
    }

    suspend fun request(url: String, form: Map<String, String>? = null): Page = lock.withLock {
        val original = OgladajAnimeParser.siteUrl(url) ?: throw Problem("nieprawidłowy adres strony.")
        val target = if (cookie(original) == null) bootstrap(original) else original
        try {
            exchange(target, form)
        } catch (_: ExpiredSession) {
            cookies.remove(target.toHttpUrl().host)
            try { exchange(bootstrap(target), form) }
            catch (expired: ExpiredSession) { throw Problem("sesja wygasła (HTTP ${expired.status}).") }
        }
    }

    private class ExpiredSession(val status: Int) : Exception()

    private suspend fun exchange(url: String, form: Map<String, String>?): Page {
        var target = url
        repeat(5) {
            val httpUrl = target.toHttpUrl()
            val headers = buildMap {
                put("Accept-Language", "pl,en;q=0.7")
                cookie(target)?.let { put("Cookie", "${it.name}=${it.value}") }
                if (form != null) {
                    put("Referer", URI(target).resolve("/").toString())
                    put("X-Requested-With", "XMLHttpRequest")
                }
            }
            fetch(target, form, headers).use { response ->
                Cookie.parseAll(httpUrl, response.headers).filter { it.name == "PHPSESSID" }.forEach {
                    if (it.value.isBlank() || it.expiresAt <= System.currentTimeMillis()) cookies.remove(httpUrl.host)
                    else cookies[httpUrl.host] = it
                }
                when (response.code) {
                    in 200..299 -> return Page(target, response.body.string())
                    301, 302, 303, 307, 308 -> {
                        // Only GET follows ordinary redirects. A form can be replayed only on its original origin.
                        val next = response.header("Location")?.takeIf { it.isNotBlank() }?.let { location ->
                            runCatching { URI(target).resolve(location).toString() }.getOrNull()
                        }?.let(OgladajAnimeParser::siteUrl)
                        if (next == null || (form != null && (response.code !in setOf(307, 308) || next.toHttpUrl().host != httpUrl.host))) {
                            throw Problem("strona zwróciła niedozwolone przekierowanie.")
                        }
                        target = next
                    }
                    401, 419 -> if (form != null) throw ExpiredSession(response.code) else throw Problem("błąd HTTP ${response.code}.")
                    403, 429 -> throw Problem("strona odrzuciła połączenie (HTTP ${response.code}). Sprawdź dostęp w przeglądarce.")
                    else -> throw Problem("błąd HTTP ${response.code}.")
                }
            }
        }
        throw Problem("zbyt wiele przekierowań.")
    }
}
