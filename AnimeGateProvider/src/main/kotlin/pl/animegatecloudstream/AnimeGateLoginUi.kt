package pl.animegatecloudstream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AnimeGateLoginUi {
    @Suppress("SetJavaScriptEnabled", "DEPRECATION", "OVERRIDE_DEPRECATION")
    fun show(context: Context, session: AnimeGateSession) {
        if (Looper.myLooper() != Looper.getMainLooper()) { Handler(Looper.getMainLooper()).post { show(context, session) }; return }
        val activity = activity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) { toast(context, "Otwórz ustawienia dodatku w CloudStream."); return }
        val web = try { WebView(activity) } catch (_: Exception) { toast(context, "Nie można uruchomić Android System WebView."); return }
        val agent = web.settings.userAgentString
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; useWideViewPort = true; loadWithOverviewMode = true
            allowFileAccess = false; allowContentAccess = false; allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false); setGeolocationEnabled(false)
            saveFormData = false; savePassword = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        web.webChromeClient = object : WebChromeClient() {
            // Auth pages may log sensitive callback data. Do not forward console messages.
            override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
        }
        val manager = CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        val status = TextView(activity).apply { text = "Ładowanie logowania…"; setPadding(16, 4, 16, 4) }
        val dialog = AnimeGateLoginDialog(activity, web, status)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val observedSession = AnimeGateObservedSession()
        var sawDiscord = false
        var returned = false
        var pageError = false

        fun busy(value: Boolean) {
            dialog.saveButton.isEnabled = !value; dialog.clearButton.isEnabled = !value
        }

        fun blocked(url: String, preservePage: Boolean): Boolean {
            val reason = AnimeGateAuthPolicy.mainBlockReason(url) ?: return false
            val update = {
                if (dialog.isShowing && !(returned && preservePage &&
                    reason == AnimeGateAuthPolicy.BlockReason.HOST_NOT_ALLOWED && AnimeGateAuthPolicy.canCapture(web.url.orEmpty()))) {
                    pageError = true
                    status.text = "Niedozwolone przekierowanie logowania. Wróć do AnimeGate i spróbuj ponownie."
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) update() else web.post { update() }
            return true
        }

        fun check(url: String, cookies: String) {
            if (cookies.isBlank()) { status.text = "Brak sesji do sprawdzenia. Zakończ logowanie na stronie."; return }
            busy(true); status.text = "Sprawdzanie sesji AnimeGate…"
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) { AnimeGateProbe.request(url, cookies, agent) }
                    if (!dialog.isShowing) return@launch
                    val evidence = response.evidence
                    // Native CookieManager enforces each Set-Cookie domain/path. Never send these to Discord.
                    response.applyCookies { manager.setCookie(url, it) }
                    manager.flush()
                    if (evidence.status in 200..299) {
                        session.save(url, manager.getCookie(url).orEmpty().ifBlank { cookies }, agent)
                    }
                    status.text = when (evidence.outcome) {
                        AnimeGateProbe.Outcome.VERIFIED_SESSION_API -> "Zalogowano. Możesz zamknąć okno i wybrać odcinek."
                        AnimeGateProbe.Outcome.PAGE_CANDIDATE -> "Zapisano sesję. Otwórz katalog, aby sprawdzić dostęp."
                        AnimeGateProbe.Outcome.LOGIN_REQUIRED -> "Serwis nie potwierdził zalogowania. Dokończ logowanie i wymagane członkostwo na Discordzie."
                        AnimeGateProbe.Outcome.ACCESS_DENIED -> "Serwis odmówił dostępu (HTTP 403). To nie potwierdza wygaśnięcia sesji."
                        AnimeGateProbe.Outcome.RATE_LIMITED -> "Serwis ograniczył żądania (HTTP 429). Odczekaj przed kolejną ręczną próbą."
                        AnimeGateProbe.Outcome.TLS_ERROR -> "Błąd certyfikatu HTTPS. Połączenie zostało przerwane."
                        AnimeGateProbe.Outcome.TIMEOUT -> "Sprawdzenie przekroczyło czas oczekiwania."
                        AnimeGateProbe.Outcome.NETWORK_ERROR -> "Błąd połączenia podczas sprawdzania sesji."
                        else -> "Nie udało się potwierdzić sesji. Dokończ logowanie i spróbuj ponownie."
                    }
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { status.text = "Nie udało się zapisać sesji. Spróbuj ponownie."
                } finally { busy(false) }
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.isForMainFrame && blocked(request.url.toString(), true)
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !AnimeGateAuthPolicy.allowLegacyNavigation(url) && blocked(url, true)
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                observedSession.observe(request.method, url)
                if (request.isForMainFrame && blocked(url, false)) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream("Niedozwolone przekierowanie logowania.".toByteArray()))
                }
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageError = false
                if (AnimeGateAuthPolicy.isDiscord(url)) sawDiscord = true
                if (sawDiscord && AnimeGateAuthPolicy.isSite(url)) returned = true
                status.text = if (AnimeGateAuthPolicy.isDiscord(url)) "Dokończ logowanie i zgodę na stronie Discorda." else "Ładowanie AnimeGate…"
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!pageError) {
                    status.text = if (AnimeGateAuthPolicy.isDiscord(url)) "Dokończ logowanie na Discordzie."
                        else if (session.hasSession()) "Sesja jest zapisana. Możesz ją odnowić przyciskiem „Zapisz”."
                        else "Po zalogowaniu wybierz „Zapisz”."
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame) return
                pageError = true
                status.text = "Strona zwróciła HTTP ${response.statusCode}."
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                pageError = true; status.text = "Nie udało się wczytać strony."
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel(); pageError = true; status.text = "Błąd certyfikatu HTTPS. Logowanie zostało przerwane."
            }
        }
        dialog.saveButton.setOnClickListener {
            if (!AnimeGateAuthPolicy.canCapture(web.url.orEmpty())) { status.text = "Najpierw zakończ logowanie i wróć do strony AnimeGate."; return@setOnClickListener }
            val url = observedSession.url() ?: AnimeGateAuthPolicy.ROOT
            check(url, manager.getCookie(url).orEmpty())
        }
        dialog.clearButton.setOnClickListener {
            val targets = listOfNotNull(AnimeGateAuthPolicy.ROOT, AnimeGateAuthPolicy.SESSION, session.requestUrl(), observedSession.url()).distinct()
            for (target in targets) {
                val names = (session.cookieHeader(target) + ";" + manager.getCookie(target).orEmpty())
                    .split(';').map { it.substringBefore('=').trim() }.filter { it.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) }.distinct()
                val uri = URI(target)
                val parts = uri.rawPath.split('/').filter(String::isNotBlank)
                val paths = setOf("/", "/auth", "/api", "/api/auth", "/api/auth/session") + parts.indices.map { "/" + parts.take(it + 1).joinToString("/") }
                for (name in names) for (path in paths.flatMap { listOf(it, it.trimEnd('/') + "/") }.distinct()) {
                    val expired = "$name=; Max-Age=0; Path=$path; Secure; HttpOnly"
                    manager.setCookie(target, expired)
                    for (domain in setOf(uri.host, "animegate.pl")) manager.setCookie(target, "$expired; Domain=$domain")
                }
            }
            session.clear()
            manager.flush(); status.text = "Wylogowano z AnimeGate."; web.loadUrl(AnimeGateAuthPolicy.LOGIN)
        }
        dialog.setOnDismissListener {
            scope.cancel(); web.stopLoading(); web.clearFormData(); (web.parent as? ViewGroup)?.removeView(web); web.destroy()
        }
        dialog.show(); web.requestFocus(); web.loadUrl(if (session.hasSession()) AnimeGateAuthPolicy.ROOT else AnimeGateAuthPolicy.LOGIN)
    }

    private fun activity(context: Context): Activity? {
        var current = context
        val seen = mutableSetOf<Context>()
        while (seen.add(current)) { if (current is Activity) return current; current = (current as? ContextWrapper)?.baseContext ?: return null }
        return null
    }
    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
