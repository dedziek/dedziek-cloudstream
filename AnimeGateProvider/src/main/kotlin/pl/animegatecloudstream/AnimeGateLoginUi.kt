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
import java.net.URI
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
        val observedSession = AnimeGateObservedSession()
        val apiSeen = AtomicBoolean(false)
        val authSeen = AtomicBoolean(false)
        val redirects = AnimeGateRedirectDiagnostics()
        val catalog = AnimeGateCatalogContract()
        val routes = linkedSetOf<String>()
        var sawDiscord = false
        var returned = false
        var browserState = "LOADING"
        var evidence = AnimeGateProbe.Evidence(AnimeGateProbe.Outcome.UNCONFIRMED)
        var checked = false
        var checkedEndpoint = "NONE"
        var postLoginExternalBlocks = 0

        fun busy(value: Boolean) {
            dialog.saveButton.isEnabled = !value; dialog.checkButton.isEnabled = !value; dialog.clearButton.isEnabled = !value; dialog.catalogButton.isEnabled = !value
        }
        fun report(): String = buildString {
            appendLine("AnimeGate discovery prototype v8")
            appendLine("browser_state=$browserState")
            appendLine("api_seen=${apiSeen.get()}")
            appendLine("auth_seen=${authSeen.get()}")
            appendLine("discord_seen=$sawDiscord")
            appendLine("returned_to_animegate=$returned")
            val observed = observedSession.url()
            appendLine("session_api_observed=${observed != null}")
            appendLine("observed_session_origin=${when { observed == null -> "NONE"; AnimeGateAuthPolicy.isApi(observed) -> "API"; else -> "SITE" }}")
            val savedUrl = session.requestUrl()
            appendLine("saved_session_origin=${when { savedUrl == null -> "NONE"; AnimeGateAuthPolicy.isApi(savedUrl) -> "API"; else -> "SITE" }}")
            appendLine("saved_candidate=${session.hasSession()}")
            appendLine("agent_matches=${session.hasSession() && session.agent() == agent}")
            appendLine("http_checked=$checked")
            appendLine("http_status=${evidence.status}")
            appendLine("http_result=${evidence.outcome.name}")
            appendLine("http_endpoint=$checkedEndpoint")
            appendLine("http_json_shape=${evidence.jsonShape}")
            appendLine("post_login_external_blocks=$postLoginExternalBlocks")
            appendLine("signout_control=${evidence.signOut}")
            appendLine("catalog_links=${evidence.catalogLinks}")
            append(redirects.report())
            append(catalog.report())
            synchronized(routes) { routes.forEach { appendLine("api_route=$it") } }
        }

        fun blocked(url: String, source: AnimeGateRedirectDiagnostics.Source): Boolean {
            val block = redirects.record(url, source) ?: return false
            // The request interceptor runs on a worker thread; only update views on main.
            val update = {
                if (dialog.isShowing) {
                    if (returned && source != AnimeGateRedirectDiagnostics.Source.MAIN_REQUEST &&
                        block.reason == AnimeGateAuthPolicy.BlockReason.HOST_NOT_ALLOWED && AnimeGateAuthPolicy.canCapture(web.url.orEmpty())) {
                        // Cancelling an outgoing link keeps the current AnimeGate page and login status intact.
                        postLoginExternalBlocks++
                    } else {
                        browserState = "REDIRECT_BLOCKED"
                        status.text = "Zablokowano przejście do ${block.host} (${block.reason.name}). Wybierz „Raport” — zawiera domenę i powód bez tokenów."
                    }
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
                    checked = true; evidence = response.evidence; checkedEndpoint = AnimeGateAuthPolicy.probeKind(url)
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
                if (AnimeGateAuthPolicy.isAuth(url)) authSeen.set(true)
                observedSession.observe(request.method, url)
                catalog.observe(request.method, url)
                AnimeGateAuthPolicy.safeApiPath(url)?.let { route ->
                    val host = when { AnimeGateAuthPolicy.isApi(url) -> "api.animegate.pl"; AnimeGateAuthPolicy.isAuth(url) -> "auth.animegate.pl"; else -> "animegate.pl" }
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
                if (AnimeGateAuthPolicy.isAuth(url)) authSeen.set(true)
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
            val url = observedSession.url() ?: AnimeGateAuthPolicy.ROOT
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
        dialog.catalogButton.setOnClickListener {
            val requests = catalog.requests()
            if (requests.isEmpty()) {
                catalog.finish("NO_ROUTES")
                status.text = "Najpierw otwórz na stronie wyszukiwanie, kartę anime i odcinek, potem wybierz „Test katalogu”."
                return@setOnClickListener
            }
            val authUrl = session.requestUrl()
            if (authUrl == null || session.agent() != agent) {
                catalog.finish("SESSION_REQUIRED"); status.text = "Najpierw wybierz „Zapisz i sprawdź”, aby zapisać bieżącą sesję."; return@setOnClickListener
            }
            val authCookies = manager.getCookie(authUrl).orEmpty().ifBlank { session.cookieHeader(authUrl) }
            if (authCookies.isBlank()) {
                catalog.finish("SESSION_REQUIRED"); status.text = "Brak sesji do testu. Zaloguj się i wybierz „Zapisz i sprawdź”."; return@setOnClickListener
            }
            busy(true); catalog.start(); status.text = "Sprawdzanie sesji przed testem katalogu…"
            scope.launch {
                try {
                    val proof = withContext(Dispatchers.IO) { AnimeGateProbe.request(authUrl, authCookies, agent) }
                    checked = true; checkedEndpoint = AnimeGateAuthPolicy.probeKind(authUrl); evidence = proof.evidence
                    proof.applyCookies { manager.setCookie(authUrl, it) }; manager.flush()
                    if (proof.evidence.outcome != AnimeGateProbe.Outcome.VERIFIED_SESSION_API) {
                        catalog.finish("SESSION_REQUIRED"); status.text = "Sesja nie została potwierdzona (${evidence.outcome.name}). Wybierz „Raport”."; return@launch
                    }
                    var finalState = "DONE"
                    for ((index, request) in requests.withIndex()) {
                        status.text = "Test katalogu: ${index + 1}/${requests.size}…"
                        // Read the browser's proper domain/path cookie scope, never reuse auth/me's header here.
                        val cookies = manager.getCookie(request.url).orEmpty()
                        val result = withContext(Dispatchers.IO) { catalog.request(request, cookies, agent) }
                        if (result.status == 403 || result.status == 429) { finalState = "STOPPED_${result.status}"; break }
                        if (result.status == 401) { finalState = "SESSION_REQUIRED"; break }
                        if (result.status == 0) { finalState = "ERROR"; break }
                    }
                    catalog.finish(finalState)
                    status.text = "Test katalogu: $finalState. Wybierz „Raport” — zawiera tylko trasy i strukturę odpowiedzi."
                } catch (error: CancellationException) { catalog.finish("CANCELLED"); throw error
                } catch (_: Exception) { catalog.finish("ERROR"); status.text = "Nie udało się ukończyć testu katalogu. Wybierz „Raport”."
                } finally { busy(false) }
            }
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
            manager.flush(); checked = false; status.text = "Usunięto zapis i cookies obsługiwanych adresów testu AnimeGate. Pozostałe serwisy zachowują swoje sesje."
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
