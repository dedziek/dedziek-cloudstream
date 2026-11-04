package pl.animegatecloudstream

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import java.util.concurrent.atomic.AtomicBoolean
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
        val sessionApi = AtomicBoolean(false)
        val apiSeen = AtomicBoolean(false)
        val redirects = AnimeGateRedirectDiagnostics()
        val routes = linkedSetOf<String>()
        var sawDiscord = false
        var returned = false
        var browserState = "LOADING"
        var evidence = AnimeGateProbe.Evidence(AnimeGateProbe.Outcome.UNCONFIRMED)
        var checked = false

        fun busy(value: Boolean) {
            dialog.saveButton.isEnabled = !value; dialog.checkButton.isEnabled = !value; dialog.clearButton.isEnabled = !value
        }
        fun report(): String = buildString {
            appendLine("AnimeGate login prototype v3")
            appendLine("browser_state=$browserState")
            appendLine("api_seen=${apiSeen.get()}")
            appendLine("discord_seen=$sawDiscord")
            appendLine("returned_to_animegate=$returned")
            appendLine("session_api_observed=${sessionApi.get()}")
            appendLine("saved_candidate=${session.hasSession()}")
            appendLine("agent_matches=${session.hasSession() && session.agent() == agent}")
            appendLine("http_checked=$checked")
            appendLine("http_status=${evidence.status}")
            appendLine("http_result=${evidence.outcome.name}")
            appendLine("signout_control=${evidence.signOut}")
            appendLine("catalog_links=${evidence.catalogLinks}")
            append(redirects.report())
            synchronized(routes) { routes.forEach { appendLine("api_route=$it") } }
        }

        fun blocked(url: String, source: AnimeGateRedirectDiagnostics.Source): Boolean {
            val block = redirects.record(url, source) ?: return false
            // The request interceptor runs on a worker thread; only update views on main.
            val update = {
                if (dialog.isShowing) {
                    browserState = "REDIRECT_BLOCKED"
                    status.text = "Zablokowano przejście do ${block.host} (${block.reason.name}). Wybierz „Raport” — zawiera domenę i powód bez tokenów."
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) update() else web.post { update() }
            return true
        }

        fun check(url: String, cookies: String, persist: Boolean) {
            if (cookies.isBlank()) { status.text = "Brak sesji do sprawdzenia. Zakończ logowanie na stronie."; return }
            busy(true); status.text = "Sprawdzanie sesji AnimeGate…"
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) { AnimeGateProbe.request(url, cookies, agent) }
                    if (!dialog.isShowing) return@launch
                    checked = true; evidence = response.evidence
                    // Native CookieManager enforces each Set-Cookie domain/path. Never send these to Discord.
                    response.applyCookies { manager.setCookie(url, it) }
                    manager.flush()
                    if (persist && evidence.status in 200..299) {
                        session.save(url, manager.getCookie(url).orEmpty().ifBlank { cookies }, agent)
                    }
                    status.text = when (evidence.outcome) {
                        AnimeGateProbe.Outcome.VERIFIED_SESSION_API -> "API potwierdziło zalogowaną sesję. Uruchom CloudStream ponownie i użyj „Sprawdź zapis”."
                        AnimeGateProbe.Outcome.PAGE_CANDIDATE -> "Zapisano sesję do testu. Klient widzi cechy katalogu; API logowania nie zostało jeszcze potwierdzone. Skopiuj raport."
                        AnimeGateProbe.Outcome.LOGIN_REQUIRED -> "Serwis nie potwierdził zalogowania. Dokończ logowanie i wymagane członkostwo na Discordzie."
                        AnimeGateProbe.Outcome.ACCESS_DENIED -> "Serwis odmówił dostępu (HTTP 403). To nie potwierdza wygaśnięcia sesji. Skopiuj raport."
                        AnimeGateProbe.Outcome.RATE_LIMITED -> "Serwis ograniczył żądania (HTTP 429). Odczekaj przed kolejną ręczną próbą."
                        AnimeGateProbe.Outcome.TLS_ERROR -> "Błąd certyfikatu HTTPS. Połączenie zostało przerwane."
                        AnimeGateProbe.Outcome.TIMEOUT -> "Sprawdzenie przekroczyło czas oczekiwania. Skopiuj raport."
                        AnimeGateProbe.Outcome.NETWORK_ERROR -> "Błąd połączenia podczas sprawdzania sesji. Skopiuj raport."
                        else -> "Wynik wymaga sprawdzenia: ${evidence.outcome.name}, HTTP ${evidence.status}. Skopiuj raport; sam zapis nie potwierdza logowania."
                    }
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { status.text = "Nie udało się zapisać lub sprawdzić sesji. Raport nie zawiera danych logowania."
                } finally { busy(false) }
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.isForMainFrame && blocked(request.url.toString(), AnimeGateRedirectDiagnostics.Source.MAIN_NAVIGATION)
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !AnimeGateAuthPolicy.allowLegacyNavigation(url) && blocked(url, AnimeGateRedirectDiagnostics.Source.LEGACY_NAVIGATION)
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (AnimeGateAuthPolicy.isApi(url)) apiSeen.set(true)
                if (request.method == "GET" && AnimeGateAuthPolicy.observedSessionApi(url)) sessionApi.set(true)
                AnimeGateAuthPolicy.safeApiPath(url)?.let { route ->
                    val host = if (AnimeGateAuthPolicy.isApi(url)) "api.animegate.pl" else "animegate.pl"
                    synchronized(routes) { if (routes.size < 32) routes.add("$host$route") }
                }
                if (request.isForMainFrame && blocked(url, AnimeGateRedirectDiagnostics.Source.MAIN_REQUEST)) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream("Niedozwolone przekierowanie logowania.".toByteArray()))
                }
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                browserState = "LOADING"
                if (AnimeGateAuthPolicy.isApi(url)) apiSeen.set(true)
                if (AnimeGateAuthPolicy.isDiscord(url)) sawDiscord = true
                if (sawDiscord && AnimeGateAuthPolicy.isSite(url)) returned = true
                status.text = if (AnimeGateAuthPolicy.isDiscord(url)) "Dokończ logowanie i zgodę na stronie Discorda." else "Ładowanie AnimeGate…"
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (browserState == "LOADING") {
                    browserState = "PAGE_LOADED"
                    status.text = if (AnimeGateAuthPolicy.isDiscord(url)) "Dokończ logowanie na Discordzie."
                        else "Po zalogowaniu i powrocie do katalogu wybierz „Zapisz i sprawdź”."
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame) return
                browserState = "HTTP_${response.statusCode}"
                status.text = "Strona zwróciła HTTP ${response.statusCode}. Skopiuj raport; nie wykonywano automatycznych ponowień."
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                browserState = "NETWORK_ERROR"; status.text = "Nie udało się wczytać strony. Skopiuj raport."
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel(); browserState = "TLS_ERROR"; status.text = "Błąd certyfikatu HTTPS. Logowanie zostało przerwane."
            }
        }
        dialog.saveButton.setOnClickListener {
            if (!AnimeGateAuthPolicy.canCapture(web.url.orEmpty())) { status.text = "Najpierw zakończ logowanie i wróć do strony AnimeGate."; return@setOnClickListener }
            val url = if (sessionApi.get()) AnimeGateAuthPolicy.SESSION else AnimeGateAuthPolicy.ROOT
            check(url, manager.getCookie(url).orEmpty(), true)
        }
        dialog.checkButton.setOnClickListener {
            val url = session.requestUrl()
            if (url == null) { status.text = "Brak ważnego zapisu. Prototyp przechowuje sesję najwyżej godzinę."; return@setOnClickListener }
            if (session.agent() != agent) { status.text = "Zmieniła się wersja WebView. Zaloguj się ponownie, używając bieżącej przeglądarki."; return@setOnClickListener }
            check(url, session.cookieHeader(url), false)
        }
        dialog.reportButton.setOnClickListener {
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AnimeGate — raport logowania", report()))
            toast(activity, "Skopiowano raport bez haseł, tokenów i wartości ciasteczek.")
        }
        dialog.clearButton.setOnClickListener {
            val old = session.requestUrl()?.let(session::cookieHeader).orEmpty()
            val names = (old + ";" + manager.getCookie(AnimeGateAuthPolicy.ROOT).orEmpty() + ";" + manager.getCookie(AnimeGateAuthPolicy.SESSION).orEmpty())
                .split(';').map { it.substringBefore('=').trim() }.filter { it.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) }.distinct()
            session.clear()
            for (name in names) for (path in listOf("/", "/auth", "/auth/", "/api", "/api/", "/api/auth", "/api/auth/", "/api/auth/session")) {
                val expired = "$name=; Max-Age=0; Path=$path; Secure; HttpOnly"
                manager.setCookie(AnimeGateAuthPolicy.ROOT, expired)
                manager.setCookie(AnimeGateAuthPolicy.ROOT, "$expired; Domain=animegate.pl")
                manager.setCookie(AnimeGateAuthPolicy.ROOT, "$expired; Domain=.animegate.pl")
            }
            manager.flush(); checked = false; status.text = "Usunięto zapis i cookies AnimeGate dla obsługiwanych ścieżek. Pozostałe serwisy zachowują swoje sesje."
        }
        dialog.setOnDismissListener {
            scope.cancel(); web.stopLoading(); web.clearFormData(); (web.parent as? ViewGroup)?.removeView(web); web.destroy()
        }
        dialog.show(); web.requestFocus(); web.loadUrl(AnimeGateAuthPolicy.LOGIN)
    }

    private fun activity(context: Context): Activity? {
        var current = context
        val seen = mutableSetOf<Context>()
        while (seen.add(current)) { if (current is Activity) return current; current = (current as? ContextWrapper)?.baseContext ?: return null }
        return null
    }
    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
