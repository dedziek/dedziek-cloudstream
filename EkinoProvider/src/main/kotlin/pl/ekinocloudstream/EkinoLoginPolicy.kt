package pl.ekinocloudstream

import java.net.URI

/** Login-frame navigation is broader than the scope permitted for saved credentials. */
object EkinoLoginPolicy {
    private fun httpsHost(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        uri.host?.lowercase()
    }.getOrNull()

    fun isEkinoUrl(url: String): Boolean = httpsHost(url) == "ekino.ws"

    fun isBlank(url: String): Boolean = url.substringBefore('#') in setOf("about:blank", "about:srcdoc")

    fun isVerificationFrame(url: String): Boolean =
        httpsHost(url) == "challenges.cloudflare.com" || isBlank(url) ||
            (httpsHost(url) in setOf("www.google.com", "www.recaptcha.net", "www.gstatic.com") &&
                runCatching { URI(url).path.startsWith("/recaptcha/") }.getOrDefault(false))

    // The deprecated callback supplies no isForMainFrame flag. A separate request
    // interceptor still restricts every top-level GET/POST to the Ekino origin.
    fun allowLegacyNavigation(url: String): Boolean = isEkinoUrl(url) || isVerificationFrame(url)
}
