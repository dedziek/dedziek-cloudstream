package pl.animegatecloudstream

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.net.SocketTimeoutException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import org.jsoup.Jsoup

object AnimeGateProbe {
    enum class Outcome { VERIFIED_SESSION_API, PAGE_CANDIDATE, UNCONFIRMED, LOGIN_REQUIRED, ACCESS_DENIED,
        RATE_LIMITED, REDIRECT_SITE, REDIRECT_OTHER, NETWORK_ERROR, TLS_ERROR, TIMEOUT }
    data class Evidence(val outcome: Outcome, val status: Int = 0, val signOut: Boolean = false, val catalogLinks: Boolean = false, val jsonShape: String = "NONE")
    // Deliberately not a data class: response cookies must never appear in toString/logs.
    class Response(val evidence: Evidence, private val rotations: List<String> = emptyList()) {
        fun applyCookies(accept: (String) -> Unit) = rotations.forEach(accept)
    }

    fun classify(url: String, status: Int, body: String, location: String? = null, truncated: Boolean = false): Evidence {
        require(AnimeGateAuthPolicy.isProbeUrl(url))
        if (status == 401) return Evidence(Outcome.LOGIN_REQUIRED, status)
        if (status == 403) return Evidence(Outcome.ACCESS_DENIED, status)
        if (status == 429) return Evidence(Outcome.RATE_LIMITED, status)
        if (status in 300..399) {
            val destination = location?.let { runCatching { URI(url).resolve(it).toString() }.getOrNull() }
            return Evidence(when (destination?.let(AnimeGateAuthPolicy::redirectKind)) {
                "LOGIN", "DISCORD" -> Outcome.LOGIN_REQUIRED
                "SITE" -> Outcome.REDIRECT_SITE
                else -> Outcome.REDIRECT_OTHER
            }, status)
        }
        if (status !in 200..299 || truncated) return Evidence(Outcome.UNCONFIRMED, status)
        if (AnimeGateAuthPolicy.isSessionProbeUrl(url)) {
            val result = AnimeGateSessionResponse.inspect(body, AnimeGateAuthPolicy.isCurrentUserProbeUrl(url))
            return Evidence(result.outcome, status, jsonShape = result.schema)
        }
        val doc = Jsoup.parse(body, url)
        val controls = doc.select("a,button,[role=button],form")
        val logout = controls.any { node ->
            node.text().trim().matches(Regex("(?i)wyloguj(?: się)?|sign out|log out")) ||
                sequenceOf(node.attr("href"), node.attr("action")).any { it.substringBefore('?').trimEnd('/').endsWith("/logout") || it.substringBefore('?').trimEnd('/').endsWith("/signout") }
        }
        val anime = doc.select("a[href]").any { node ->
            runCatching { val link = URI(url).resolve(node.attr("href")); AnimeGateAuthPolicy.isSite(link.toString()) && link.path.startsWith("/anime/") }.getOrDefault(false)
        }
        // HTML signs are diagnostic only. A shell/cookie or logout link alone is not proof of authentication.
        return Evidence(if (logout && anime) Outcome.PAGE_CANDIDATE else Outcome.UNCONFIRMED, status, logout, anime)
    }

    fun request(url: String, cookies: String, agent: String): Response {
        require(AnimeGateAuthPolicy.isProbeUrl(url))
        require(cookies.isNotBlank() && cookies.length <= 65_536 && !cookies.contains('\r') && !cookies.contains('\n'))
        require(agent.isNotBlank() && agent.length <= 4096 && !agent.contains('\r') && !agent.contains('\n'))
        var connection: HttpsURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", if (AnimeGateAuthPolicy.isSessionProbeUrl(url)) "application/json" else "text/html")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            val bytes = ByteArrayOutputStream()
            (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { input ->
                val buffer = ByteArray(8192)
                while (bytes.size() <= 1_048_576) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - bytes.size()))
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                }
            }
            val evidence = classify(url, status, bytes.toString("UTF-8"), connection.getHeaderField("Location"), bytes.size() > 1_048_576)
            val rotations = connection.headerFields.entries.filter { it.key?.equals("Set-Cookie", true) == true }.flatMap { it.value.orEmpty() }
            Response(evidence, rotations)
        } catch (_: SSLException) { Response(Evidence(Outcome.TLS_ERROR))
        } catch (_: SocketTimeoutException) { Response(Evidence(Outcome.TIMEOUT))
        } catch (_: Exception) { Response(Evidence(Outcome.NETWORK_ERROR))
        } finally { connection?.disconnect() }
    }
}
