package pl.dedziek.ekinotv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

internal interface SiteBrowser {
    suspend fun page(url: String): WebPage
    suspend fun search(query: String): WebPage
    suspend fun player(material: String, player: Player): WebPage
    suspend fun bridge(url: String): List<String>
    suspend fun userAgent(): String
}

/** The site is never fetched by a native HTTP client. One browser owns its complete session. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class WebSession(context: Context, val state: SessionState,
                          private val activitySource: () -> Activity? = { CommonActivity.activity }) : SiteBrowser {
    val operations = Mutex()
    private val mapper = ObjectMapper()
    private var web: WebView? = null
    private var owner: Activity? = null
    private var dialog: Dialog? = null
    private var info: TextView? = null
    private var mainFinished = false
    private var failure: String? = null
    private var external: String? = null
    private var bridgeNavigation = false
    private var manual = false
    private var epoch = 0L
    private val budget = NavigationBudget()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val popups = mutableMapOf<WebView, Dialog>()
    private var activeJob: Job? = null

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                if (owner === activity) {
                    activeJob?.cancel(CancellationException("Zamknięto okno CloudStream"))
                    state.note("Zamknięto okno CloudStream")
                    closeBrowser()
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        })
    }

    private fun ensure(): WebView {
        val activity = activitySource() ?: throw SiteProblem("Ekino TV: otwórz CloudStream na pierwszym planie.")
        if (activity.isFinishing || activity.isDestroyed) throw SiteProblem("Ekino TV: okno aplikacji jest zamykane.")
        if (owner !== activity) closeBrowser()
        web?.let { return it }
        owner = activity
        return WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            // Keep WebView's actual default UA; do not spoof or copy a Chrome identity.
            webChromeClient = chrome()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    mainFinished = false
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (url != "about:blank" && url == view.url) { mainFinished = true; CookieManager.getInstance().flush() }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    if (!request.isForMainFrame) return false
                    if (Site.official(target) || Site.bridge(target) || target == "about:blank") return false
                    if (bridgeNavigation && Site.absolute(target, target) != null && !Site.challengeHost(target)) external = target
                    return true
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame || Site.wrapper(request.url.toString())) {
                        state.note("HTTP ${response.statusCode} · ${if (Site.wrapper(request.url.toString())) "odtwarzacz" else "strona"}")
                        if (response.statusCode >= 400) stop("Serwer zwrócił HTTP ${response.statusCode}", Site.wrapper(request.url.toString()))
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame || Site.wrapper(request.url.toString()))
                        stop("Błąd WebView ${error.errorCode}", Site.wrapper(request.url.toString()))
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    stop("Błąd certyfikatu HTTPS")
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    failure = "Proces Android System WebView został zamknięty"
                    state.pause(failure!!)
                    closeBrowser(renderGone = true)
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if ((request.isForMainFrame || Site.wrapper(url)) && request.method == "GET" &&
                        !budget.record(url, SystemClock.elapsedRealtime())) {
                        scope.launch { stop("Strona powtarza weryfikację lub przekierowanie", Site.wrapper(url)) }
                        return WebResourceResponse("text/plain", "UTF-8", 429, "Navigation stopped", emptyMap(),
                            java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    // Observe only the route, never the verification token or request body.
                    if (Site.official(url) && request.url.path == "/watch/verify.php")
                        state.note("Strona wysłała ${request.method} do weryfikacji odtwarzacza")
                    return null
                }
            }
            web = this
            attachBehind(this)
            state.note("Utworzono wspólną przeglądarkę katalogu i odtwarzacza")
        }
    }

    private fun chrome() = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            // Numeric client codes only. Console text can contain URLs and tokens.
            val code = Regex("(?:Turnstile|Error)[^0-9]{0,24}([0-9]{6})").find(message.message())?.groupValues?.get(1)
            if (code != null) state.note("Kod klienta Turnstile: $code")
            return false
        }
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            if (manual) return false // WebView displays the site's actual alert in the visible dialog.
            result.cancel()
            stop("Strona odtwarzacza zgłosiła błąd", Site.wrapper(url))
            return true
        }
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!manual || !isUserGesture) return false
            val activity = owner ?: return false
            val child = WebView(activity)
            child.settings.javaScriptEnabled = true
            child.settings.domStorageEnabled = true
            child.settings.allowFileAccess = false
            child.settings.allowContentAccess = false
            child.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(child, true)
            val help = Dialog(activity).apply {
                setTitle("Cloudflare — pomoc")
                setContentView(child)
                setOnDismissListener { child.stopLoading(); child.destroy(); popups.remove(child) }
            }
            child.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    val allowed = request.url.toString() == "about:blank" || Site.challengeHost(request.url.toString())
                    if (allowed && request.isForMainFrame) { help.show(); help.window?.setLayout(-1, -1) }
                    return !allowed
                }
                override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                    if (url != "about:blank" && !Site.challengeHost(url)) { v.stopLoading(); help.dismiss() }
                    else if (Site.challengeHost(url)) { help.show(); help.window?.setLayout(-1, -1) }
                }
                override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
            }
            child.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) = help.dismiss()
            }
            popups[child] = help
            (resultMsg.obj as WebView.WebViewTransport).webView = child
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun attachBehind(browser: WebView) {
        (browser.parent as? ViewGroup)?.removeView(browser)
        val root = owner?.findViewById<ViewGroup>(android.R.id.content) ?: return
        browser.alpha = 0f
        root.addView(browser, 0, ViewGroup.LayoutParams(-1, -1))
    }

    /** about:blank terminates page timers as well as network loads; stopLoading alone does not. */
    private fun stop(message: String, player: Boolean = false) {
        epoch++
        failure = message
        state.pause(message, player)
        info?.text = "$message. Automatyczne ładowanie zatrzymano."
        web?.stopLoading()
        web?.loadUrl("about:blank")
        popups.values.toList().forEach { it.dismiss() }
    }

    private fun closeBrowser(renderGone: Boolean = false) {
        epoch++
        if (renderGone) web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy(); web = null }
        dialog?.dismiss(); dialog = null; info = null
        popups.values.toList().forEach { it.dismiss() }; popups.clear()
        web?.let { (it.parent as? ViewGroup)?.removeView(it); it.stopLoading(); it.destroy() }
        web = null; owner = null
    }

    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        val generation = epoch
        ensure().evaluateJavascript(script) { value ->
            if (continuation.isActive) {
                if (epoch == generation) continuation.resume(value ?: "null")
                else continuation.cancel(CancellationException("Przeglądarka została zamknięta"))
            }
        }
    }

    private suspend fun snapshot(): BrowserSnapshot {
        val raw = evaluate(SNAPSHOT)
        val encoded = mapper.readTree(raw)?.takeIf { it.isTextual }?.asText()
            ?: throw SiteProblem("Ekino TV: nie można odczytać strony WebView.")
        require(encoded.length <= 8_000_000)
        val json = mapper.readTree(encoded)
        fun read(node: com.fasterxml.jackson.databind.JsonNode) = WebPage(node.path("url").asText(), node.path("html").asText())
        return BrowserSnapshot(read(json), json.path("frames").take(12).map(::read))
    }

    private fun begin(url: String, bridge: Boolean = false) {
        check(Site.official(url) || Site.bridge(url))
        failure = null; external = null; bridgeNavigation = bridge; mainFinished = false
        budget.reset(); epoch++
        ensure().loadUrl(url)
        state.note("Nawigacja: ${if (bridge) "strona pośrednia" else if (Site.material(url)) "materiał" else "katalog"}")
    }

    private fun checkAutomatic() {
        if (state.paused) throw SiteProblem("Ekino TV: ${state.reason}. Otwórz ustawienia dodatku → Otwórz ostatni materiał, wykonaj weryfikację i wybierz „Sprawdź i zamknij”.")
        if (manual) throw SiteProblem("Ekino TV: najpierw zamknij okno strony w ustawieniach dodatku.")
    }

    private suspend fun <T> automatic(block: suspend () -> T): T = withContext(Dispatchers.Main) {
        checkAutomatic()
        activeJob = currentCoroutineContext()[Job]
        try { withTimeout(30_000) { block() } }
        catch (error: TimeoutCancellationException) {
            stop("Przekroczono czas oczekiwania na stronę")
            throw SiteProblem("Ekino TV: strona nie odpowiedziała w ciągu 30 sekund. Kolejne próby wstrzymano; otwórz ustawienia dodatku.")
        } catch (error: CancellationException) {
            web?.stopLoading(); web?.loadUrl("about:blank")
            state.note("Anulowano operację; zakończono ładowanie")
            throw error
        } finally { activeJob = null }
    }

    private fun checkSnapshot(value: BrowserSnapshot) {
        failure?.let { throw SiteProblem("Ekino TV: $it. Żądania wstrzymano; otwórz ustawienia dodatku.") }
        value.gate()?.let { gate ->
            val player = (listOf(value.page) + value.frames).any { Site.wrapper(it.url) }
            stop(gate, player)
            throw SiteProblem("Ekino TV: $gate. W ustawieniach dodatku otwórz ostatni materiał i wybierz odtwarzacz na stronie.")
        }
    }

    private suspend fun loaded(): WebPage {
        while (true) {
            failure?.let { throw SiteProblem("Ekino TV: $it. Otwórz ustawienia dodatku.") }
            if (mainFinished) {
                val result = snapshot(); checkSnapshot(result)
                if (Site.official(result.page.url) || Site.bridge(result.page.url)) return result.page
            }
            delay(200)
        }
    }

    override suspend fun page(url: String): WebPage = automatic {
        begin(url); loaded()
    }

    override suspend fun search(query: String): WebPage = automatic {
        val browser = ensure()
        if (!Site.official(browser.url.orEmpty())) { begin("${Site.ENTRY}/"); loaded() }
        failure = null; external = null; bridgeNavigation = false; budget.reset(); epoch++
        mainFinished = false
        val value = mapper.writeValueAsString(query.take(200))
        evaluate("""(function(){var f=document.createElement('form');f.method='POST';f.action='/search/qf';var q=document.createElement('input');q.name='q';q.value=$value;f.appendChild(q);document.body.appendChild(f);HTMLFormElement.prototype.submit.call(f);return true;})()""")
        state.note("Wysłano formularz wyszukiwania w przeglądarce")
        loaded()
    }

    override suspend fun player(material: String, player: Player): WebPage = automatic {
        state.remember(material)
        if (Site.publicPage(ensure().url.orEmpty()) != Site.publicPage(material)) { begin(material); loaded() }
        failure = null; budget.reset()
        val h = mapper.writeValueAsString(player.host); val id = mapper.writeValueAsString(player.id)
        val click = evaluate("""(function(){if(typeof window.ShowPlayer!=='function')return false;window.ShowPlayer($h,$id);return true;})()""")
        if (click != "true") throw SiteProblem("Ekino TV: strona zmieniła sposób uruchamiania odtwarzacza.")
        state.note("Wybrano zwykły odtwarzacz: ${player.host}")
        var emptySince: Long? = null
        // The site's own ShowPlayer runs its advertised countdown; no synthesized wrapper request.
        while (true) {
            val value = snapshot(); checkSnapshot(value)
            val wrapper = value.frames.firstOrNull { Site.wrapper(it.url) && Site.path(it.url).endsWith("/${player.identity}") }
            if (wrapper != null && Catalogue.embeds(wrapper).isNotEmpty()) return@automatic wrapper
            if (wrapper != null) {
                val now = SystemClock.elapsedRealtime()
                if (emptySince == null) emptySince = now
                if (now - emptySince >= 1000 && wrapper.doc.body().text().isNotBlank()) return@automatic wrapper
            } else emptySince = null
            delay(250)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    override suspend fun bridge(url: String): List<String> = automatic {
        begin(url, true)
        while (true) {
            external?.let { return@automatic listOf(it) }
            failure?.let { throw SiteProblem("Ekino TV: $it") }
            if (mainFinished) {
                val value = snapshot(); checkSnapshot(value)
                val links = Catalogue.embeds(value.page)
                if (links.isNotEmpty()) return@automatic links
            }
            delay(200)
        }
        @Suppress("UNREACHABLE_CODE") emptyList()
    }

    override suspend fun userAgent(): String = withContext(Dispatchers.Main) { ensure().settings.userAgentString }

    suspend fun finishPlayback() = withContext(NonCancellable + Dispatchers.Main) {
        epoch++
        web?.stopLoading(); web?.loadUrl("about:blank")
        CookieManager.getInstance().flush()
    }

    fun settings(context: Context) {
        val activity = context as? Activity ?: activitySource() ?: return
        activity.runOnUiThread {
            AlertDialog.Builder(activity).setTitle("Ekino TV v1")
                .setMessage(if (state.paused) "Wstrzymano: ${state.reason}" else "Katalog i weryfikacja korzystają z tej samej przeglądarki.")
                .setItems(arrayOf("Otwórz ostatni materiał", "Otwórz stronę / logowanie", "Kopiuj raport")) { _, index ->
                    when (index) {
                        0 -> openWindow(activity, state.lastMaterial ?: "${Site.ENTRY}/")
                        1 -> openWindow(activity, "${Site.ENTRY}/")
                        2 -> {
                            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Ekino TV", state.report()))
                            android.widget.Toast.makeText(activity, "Skopiowano raport", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }.setNegativeButton("Zamknij", null).show()
        }
    }

    private fun openWindow(activity: Activity, url: String) {
        if (!operations.tryLock()) {
            android.widget.Toast.makeText(activity, "Zaczekaj na zakończenie bieżącej operacji.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        try {
            val browser = ensure()
            manual = true
            (browser.parent as? ViewGroup)?.removeView(browser); browser.alpha = 1f
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            info = TextView(activity).apply {
                text = "Ekino TV: wybierz odtwarzacz na stronie i wykonaj weryfikację. Potem naciśnij „Sprawdź i zamknij”."
                setPadding(16, 12, 16, 8)
            }
            content.addView(info)
            content.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
            val controls = LinearLayout(activity)
            fun button(label: String, action: () -> Unit) { controls.addView(Button(activity).apply {
                text = label; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f)) }
            val window = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(content)
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }
            var watchdog: Job? = null
            button("Wczytaj ponownie") { begin(url) }
            button("Sprawdź i zamknij") {
                scope.launch {
                    try {
                        if (state.accept(snapshot())) window.dismiss()
                        else info?.text = "Weryfikacja nie jest zakończona. Otwórz zwykły odtwarzacz i poczekaj na jego link. Zielony znacznik CAPTCHA nie potwierdza jeszcze dostępu."
                    } catch (_: Exception) { info?.text = "Nie można odczytać strony. Sprawdź połączenie i działanie WebView." }
                }
            }
            button("Zamknij") { window.dismiss() }
            content.addView(controls)
            window.setOnDismissListener {
                epoch++
                watchdog?.cancel()
                manual = false; info = null; dialog = null
                popups.values.toList().forEach { it.dismiss() }
                CookieManager.getInstance().flush()
                // Closing a page must also stop its reload timers, regardless of verification outcome.
                if (web === browser) {
                    browser.stopLoading(); browser.loadUrl("about:blank")
                    attachBehind(browser)
                }
                operations.unlock()
            }
            dialog = window
            window.show(); window.window?.setLayout(-1, -1)
            begin(url); browser.requestFocus()
            watchdog = scope.launch {
                delay(180_000)
                if (dialog === window) stop("Upłynęły 3 minuty; przerwano oczekiwanie na weryfikację", state.playerRequired)
            }
        } catch (error: Exception) {
            manual = false
            if (operations.isLocked) operations.unlock()
            throw error
        }
    }

    companion object {
        /** Read-only DOM access. No JS bridge, token interception, fetch replacement or browser API patches. */
        internal val SNAPSHOT = """(function(){var out={url:location.href,html:document.documentElement.outerHTML,frames:[]};function visit(w,depth){if(depth>3||out.frames.length>=12)return;for(var i=0;i<w.frames.length;i++){try{var f=w.frames[i],u=new URL(f.location.href);if(u.protocol==='https:'&&['ekino.ws','ekino-tv.pl','play.ekino.link'].indexOf(u.hostname)>=0&&f.document.readyState!=='loading'){out.frames.push({url:u.href,html:f.document.documentElement.outerHTML});visit(f,depth+1);}}catch(e){}}}visit(window,0);return JSON.stringify(out);})()"""
    }
}
