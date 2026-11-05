package pl.dedziek.ekinotv

/** A DOM belongs to a navigation, not to the last onPageFinished callback. */
internal class DocumentTarget(private val url: String?, private val previousId: Double?) {
    fun fresh(snapshot: BrowserSnapshot): Boolean =
        (Site.official(snapshot.page.url) || Site.bridge(snapshot.page.url)) &&
            snapshot.documentId.isFinite() && snapshot.documentId > 0 && snapshot.documentId != previousId

    fun matchesUrl(candidate: String): Boolean {
        if (url == null) return Site.official(candidate) &&
            (Site.path(candidate) == "/search" || Site.path(candidate).startsWith("/search/"))
        val sameOriginFamily = (Site.official(url) && Site.official(candidate)) ||
            (Site.bridge(url) && Site.bridge(candidate))
        return sameOriginFamily && Site.path(url).trimEnd('/') == Site.path(candidate).trimEnd('/')
    }

    fun ready(snapshot: BrowserSnapshot) = fresh(snapshot) && matchesUrl(snapshot.page.url) && snapshot.parsed
}
