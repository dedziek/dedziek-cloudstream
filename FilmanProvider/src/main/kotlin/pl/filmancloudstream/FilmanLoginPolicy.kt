package pl.filmancloudstream

import java.net.URI

/** Login-frame navigation is broader than the scope permitted for saved credentials. */
object FilmanLoginPolicy {
    private fun httpsHost(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        uri.host?.lowercase()
    }.getOrNull()

    fun isFilmanUrl(url: String): Boolean = httpsHost(url) == "filman.cc"

    fun isVerificationFrame(url: String): Boolean =
        httpsHost(url) == "challenges.cloudflare.com" || url.substringBefore('#') in setOf("about:blank", "about:srcdoc")

    // The deprecated callback supplies no isForMainFrame flag. A separate request
    // interceptor still restricts every top-level GET/POST to the Filman origin.
    fun allowLegacyNavigation(url: String): Boolean = isFilmanUrl(url) || isVerificationFrame(url)
}
