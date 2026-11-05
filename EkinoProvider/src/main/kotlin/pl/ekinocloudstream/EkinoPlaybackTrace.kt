package pl.ekinocloudstream

import java.net.URI
import org.jsoup.nodes.Document

/** Passive, per-playback diagnostics. Never retain URLs, HTML, cookie values or exception messages. */
internal class EkinoPlaybackTrace(savedSession: Boolean, savedAgent: Boolean) {
    private val events = mutableListOf<String>()
    private var requests = 0
    private var result = "w toku"
    private var omitted = 0

    init { note("sesja=${yes(savedSession)}, UA=${if (savedAgent) "zapisany WebView" else "domyślny CloudStream"}") }

    @Synchronized private fun note(value: String) {
        // Keep the initial session and the last response, even on pages with many players.
        if (events.size == 32) { events.removeAt(6); omitted++ }
        events.add(value)
    }

    @Synchronized fun request(url: String, cookieHeader: String) {
        requests++
        note("#$requests ${stage(url)}: cookies=${yes(cookieHeader.isNotBlank())}, PHP=${yes(hasCookie(cookieHeader, "PHPSESSID"))}, clearance=${yes(hasCookie(cookieHeader, "cf_clearance"))}")
    }

    fun cookiesUpdated(before: String, after: String) {
        fun php(header: String) = header.split(';').firstOrNull {
            it.substringBefore('=').trim() == "PHPSESSID" && it.contains('=')
        }?.substringAfter('=')?.trim()
        val old = php(before)
        val new = php(after)
        if (old != new) note("PHP po odpowiedzi=${when { old == null -> "utworzona"; new == null -> "usunięta"; else -> "zmieniona" }}")
    }

    @Synchronized fun hasRequests(): Boolean = requests > 0

    fun response(status: Int, doc: Document? = null) {
        val kind = when {
            doc == null -> ""
            EkinoParser.isChallenge(doc) -> ", Cloudflare"
            EkinoParser.isPlayerVerification(doc) -> ", CAPTCHA=${captchaKind(doc)}"
            EkinoParser.isLoginPage(doc) -> ", logowanie"
            else -> ", bez weryfikacji"
        }
        note("HTTP $status$kind")
    }

    fun players(count: Int, delay: Int?) { note("odtwarzacze=$count, odliczanie=${delay ?: "nieznane"}") }
    fun embeds(count: Int) { note("odnośniki=$count") }
    fun host(url: String) {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        note("hosting=${host.takeIf { it.length <= 100 && it.matches(Regex("[a-zA-Z0-9.-]+")) } ?: "inny"}")
    }

    @Synchronized fun finish(outcome: Outcome, paused: Boolean) {
        result = "${outcome.label}; żądania wstrzymane=${yes(paused)}"
    }

    @Synchronized fun report(): String {
        val lines = events.toMutableList()
        if (omitted > 0) lines.add(6, "… pominięto $omitted wpisów …")
        return "Ekino v10 · raport odtwarzania\n" + lines.joinToString("\n") + "\nwynik=$result; żądania=$requests"
    }

    enum class Outcome(val label: String) { VIDEO("znaleziono wideo"), EMPTY("brak wideo"), ERROR("błąd"), CANCELLED("anulowano") }

    private fun stage(url: String): String = when {
        EkinoParser.isBridgeUrl(url) -> "strona pośrednia"
        !EkinoParser.isSiteUrl(url) -> "inne pochodzenie"
        else -> {
            val path = URI(url).path.orEmpty()
            if (path.startsWith("/watch/f/")) {
                val host = path.removePrefix("/watch/f/").substringBefore('/')
                "ramka ${host.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,32}")) } ?: "inna"}"
            } else "strona materiału"
        }
    }

    private fun captchaKind(doc: Document): String = when {
        doc.selectFirst(".cf-turnstile") != null -> "Turnstile"
        doc.selectFirst(".g-recaptcha") != null -> "reCAPTCHA"
        doc.selectFirst("[data-sitekey]") != null -> "data-sitekey"
        else -> "skrypt Turnstile"
    }

    private fun hasCookie(header: String, name: String): Boolean =
        header.split(';').any { it.substringBefore('=').trim() == name && it.contains('=') }

    private fun yes(value: Boolean): String = if (value) "tak" else "nie"
}
