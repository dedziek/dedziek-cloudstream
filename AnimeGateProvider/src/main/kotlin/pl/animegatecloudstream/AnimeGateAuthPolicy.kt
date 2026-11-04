package pl.animegatecloudstream

import java.net.URI
import java.net.URLDecoder

object AnimeGateAuthPolicy {
    enum class BlockReason { MALFORMED_URL, UNSUPPORTED_SCHEME, USER_INFO, UNSUPPORTED_PORT, HOST_NOT_ALLOWED, OAUTH_CALLBACK_ORIGIN, MALFORMED_OAUTH }
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

    // A blank interstitial is not an authenticated origin and can never supply cookies.
    fun isBlankDocument(url: String): Boolean = url.substringBefore('#') == "about:blank" && runCatching { URI(url) }.isSuccess

    fun allowMainNavigation(url: String): Boolean = mainBlockReason(url) == null

    fun mainBlockReason(url: String): BlockReason? {
        if (isBlankDocument(url)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return BlockReason.MALFORMED_URL
        if (!uri.scheme.equals("https", true)) return BlockReason.UNSUPPORTED_SCHEME
        if (uri.userInfo != null) return BlockReason.USER_INFO
        if (uri.port !in setOf(-1, 443)) return BlockReason.UNSUPPORTED_PORT
        if (uri.host.isNullOrBlank()) return BlockReason.MALFORMED_URL
        if (isSite(url)) return null
        if (!isDiscord(url)) return BlockReason.HOST_NOT_ALLOWED
        return try {
            if (uri.path.trimEnd('/') !in setOf("/oauth2/authorize", "/api/oauth2/authorize")) return null
            val callbacks = uri.rawQuery.orEmpty().split('&').filter { URLDecoder.decode(it.substringBefore('='), "UTF-8") == "redirect_uri" }
            if (callbacks.isEmpty() || (callbacks.size == 1 && isSite(URLDecoder.decode(callbacks.first().substringAfter('='), "UTF-8")))) null
            else BlockReason.OAUTH_CALLBACK_ORIGIN
        } catch (_: Exception) { BlockReason.MALFORMED_OAUTH }
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
