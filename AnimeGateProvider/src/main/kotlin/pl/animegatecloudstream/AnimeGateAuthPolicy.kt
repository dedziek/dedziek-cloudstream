package pl.animegatecloudstream

import java.net.URI
import java.net.URLDecoder

object AnimeGateAuthPolicy {
    const val BASE = "https://animegate.pl"
    const val ROOT = "$BASE/"
    const val LOGIN = "$BASE/auth/login?next=%2F"
    const val SESSION_PATH = "/api/auth/session"
    const val SESSION = "$BASE$SESSION_PATH"

    private fun https(url: String): URI? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        if (uri.host.isNullOrBlank()) return null
        uri
    }.getOrNull()

    fun isSite(url: String): Boolean = https(url)?.host.equals("animegate.pl", true)
    fun isDiscord(url: String): Boolean = https(url)?.host?.lowercase() in setOf("discord.com", "discordapp.com")

    fun allowMainNavigation(url: String): Boolean {
        if (isSite(url)) return true
        if (!isDiscord(url)) return false
        return runCatching {
            val uri = https(url) ?: return false
            if (uri.path.trimEnd('/') !in setOf("/oauth2/authorize", "/api/oauth2/authorize")) return true
            val callbacks = uri.rawQuery.orEmpty().split('&').filter { URLDecoder.decode(it.substringBefore('='), "UTF-8") == "redirect_uri" }
            callbacks.isEmpty() || (callbacks.size == 1 && isSite(URLDecoder.decode(callbacks.first().substringAfter('='), "UTF-8")))
        }.getOrDefault(false)
    }

    fun allowLegacyNavigation(url: String): Boolean = allowMainNavigation(url) ||
        url.substringBefore('#') in setOf("about:blank", "about:srcdoc") ||
        https(url)?.host?.lowercase()?.let { it == "challenges.cloudflare.com" || it == "hcaptcha.com" || it.endsWith(".hcaptcha.com") } == true

    fun canCapture(url: String): Boolean = isSite(url) && runCatching {
        val uri = URI(url)
        !uri.path.orEmpty().startsWith("/auth/") && !uri.path.orEmpty().startsWith("/api/auth/") &&
            uri.rawQuery.orEmpty().split('&').none { URLDecoder.decode(it.substringBefore('='), "UTF-8") in setOf("code", "state", "access_token", "token") }
    }.getOrDefault(false)

    // Captured Cookie headers are never widened beyond the exact URL they came from.
    fun isProbeUrl(url: String): Boolean = isSite(url) && runCatching {
        val uri = URI(url)
        uri.rawPath in setOf("/", SESSION_PATH) && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    fun observedSessionApi(url: String): Boolean = isSite(url) && runCatching { URI(url).rawPath == SESSION_PATH }.getOrDefault(false)

    fun redirectKind(url: String): String = when {
        isSite(url) -> if (runCatching { URI(url).path.orEmpty().startsWith("/auth/") }.getOrDefault(false)) "LOGIN" else "SITE"
        isDiscord(url) -> "DISCORD"
        else -> "OTHER"
    }

    /** Only fixed route vocabulary; no query, identifiers, usernames or arbitrary path values. */
    fun safeApiPath(url: String): String? {
        if (!isSite(url)) return null
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        if (!path.startsWith("/api/")) return null
        val vocabulary = setOf("api", "auth", "session", "signin", "signout", "callback", "discord", "user", "users",
            "me", "anime", "animes", "episodes", "search", "catalog", "media", "videos", "sources", "profile", "account")
        return path.split('/').filter(String::isNotBlank).take(6).joinToString("/", prefix = "/") {
            if (it in vocabulary) it else "{param}"
        }
    }
}
