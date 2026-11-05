package pl.ekinocloudstream

import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Separate ephemeral hosting cookies; never accepts the Ekino session as an argument. */
internal class EkinoHostHttp(private val agent: String) {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    fun mediaHeaders(url: String): Map<String, String> = buildMap {
        put("User-Agent", agent)
        if (EkinoHostSources.publicHttps(url)) {
            cookies.get(URI(url), emptyMap())["Cookie"].orEmpty().joinToString("; ").takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
    }

    suspend fun get(url: String, referer: String): EkinoHostResolver.Response = request(url, referer)

    suspend fun post(url: String, referer: String, body: String, token: String?): EkinoHostResolver.Response =
        request(url, referer, body, token)

    private suspend fun request(url: String, referer: String, body: String? = null, token: String? = null): EkinoHostResolver.Response = withContext(Dispatchers.IO) {
        require(EkinoHostSources.publicHttps(url))
        require(body == null || body.length <= 16_384)
        require(token == null || token.length <= 8192 && !token.contains('\r') && !token.contains('\n'))
        if (body != null) {
            // Public Byse initialization stays on the embed's exact origin.
            val target = URI(url); val parent = URI(referer)
            require(target.scheme == parent.scheme && target.rawAuthority == parent.rawAuthority)
            require(target.path.matches(Regex("/api/videos/[a-zA-Z0-9_-]{1,128}/embed/(captcha|captcha/verify|playback)")))
        }
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            if (EkinoParser.absolute(url, referer) != null) connection.setRequestProperty("Referer", referer)
            mediaHeaders(url)["Cookie"]?.let { connection.setRequestProperty("Cookie", it) }
            if (body != null) {
                val data = body.toByteArray(Charsets.UTF_8)
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(data.size)
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Origin", URI(referer).let { "${it.scheme}://${it.rawAuthority}" })
                token?.let { connection.setRequestProperty("X-Captcha-Token", it) }
                connection.outputStream.use { it.write(data) }
            }
            val status = connection.responseCode
            val setCookies = connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value.orEmpty() }
            cookies.put(URI(url), mapOf("Set-Cookie" to setCookies))
            val location = connection.getHeaderField("Location")
            if (status !in 200..299) return@withContext EkinoHostResolver.Response(status, location = location)
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (output.size() <= 1_048_576) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
            EkinoHostResolver.Response(status, if (output.size() <= 1_048_576) output.toString("UTF-8") else "")
        } finally { connection.disconnect() }
    }
}
