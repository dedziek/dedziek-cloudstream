package pl.animewatchcloudstream

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import java.util.concurrent.CancellationException
import pl.animewatchcloudstream.AnimeWatchPlayback.Stream

/** Lets the hosting's own scripts initialise the player without opening a dialog. */
object AnimeWatchBrowser {
    class VerificationRequired : Exception("Byse wymaga ręcznej weryfikacji. Otwórz ustawienia rozszerzenia AnimeWatch i wybierz „Otwórz weryfikację”.")

    class SourceBridge(private val receive: (String, String) -> Unit) {
        @JavascriptInterface
        fun postSource(nonce: String, payload: String) {
            if (payload.length <= 16_384) receive(nonce, payload)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    suspend fun resolve(embedUrl: String, referer: String, frameUrl: String? = null, interactive: Boolean = false): Stream? {
        val player = AnimeWatchPlayback.publicHttps(embedUrl) ?: return null
        if (AnimeWatchPlayback.publicHttps(referer) == null) return null
        val pages = AtomicReference(listOfNotNull(player, frameUrl?.let(AnimeWatchPlayback::publicHttps)).distinct())
        val nonce = UUID.randomUUID().toString()
        val failure = AtomicReference("Byse: odtwarzacz nie zakończył inicjalizacji w ciągu 45 sekund.")
        val verificationRequired = AtomicBoolean(false)
        val cancelledByUser = AtomicBoolean(false)
        val result = withTimeoutOrNull(if (interactive) 180_000L else 45_000L) {
            suspendCancellableCoroutine { continuation ->
                val main = Handler(Looper.getMainLooper())
                val finished = AtomicBoolean(false)
                var web: WebView? = null
                var probe: Runnable? = null
                var dialog: AlertDialog? = null

                fun finish(result: Stream?) {
                    if (!finished.compareAndSet(false, true)) return
                    main.post {
                        probe?.let(main::removeCallbacks)
                        probe = null
                        dialog?.setOnDismissListener(null)
                        dialog?.setOnCancelListener(null)
                        runCatching { dialog?.dismiss() }
                        dialog = null
                        web?.let { view ->
                            runCatching { view.stopLoading() }
                            runCatching { view.removeJavascriptInterface("AnimeWatchBridge") }
                            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                            runCatching { view.destroy() }
                        }
                        web = null
                        if (continuation.isActive) continuation.resume(result)
                    }
                }

                continuation.invokeOnCancellation { finish(null) }
                main.post open@{
                    if (finished.get() || !continuation.isActive) return@open
                    val activity = CommonActivity.activity
                    if (activity == null || activity.isFinishing || activity.isDestroyed) {
                        failure.set("Byse: brak aktywnego okna CloudStream do uruchomienia odtwarzacza.")
                        finish(null)
                        return@open
                    }
                    try {
                        val browser = WebView(activity)
                        web = browser
                        browser.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            setGeolocationEnabled(false)
                            saveFormData = false
                            savePassword = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                        }
                        browser.alpha = if (interactive) 1f else 0f
                        browser.isFocusable = interactive
                        browser.isFocusableInTouchMode = interactive
                        browser.importantForAccessibility = if (interactive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            browser.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        }
                        browser.webChromeClient = if (interactive) WebChromeClient() else object : WebChromeClient() {
                            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                result.cancel(); return true
                            }
                            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                result.cancel(); return true
                            }
                            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                                result.cancel(); return true
                            }
                        }
                        // The real Byse page embeds a different hosting origin. Its iframe
                        // needs its own session cookies; this setting applies only to this WebView.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
                        browser.setDownloadListener { _, _, _, _, _ -> }
                        browser.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) = Unit
                            override fun onViewDetachedFromWindow(view: View) { finish(null) }
                        })

                        fun stream(url: String, headers: Map<String, String>, source: String, type: String = ""): Stream {
                            val captured = headers.filterKeys {
                                it.lowercase() !in setOf("host", "connection", "content-length", "accept-encoding", "range", "referer", "cookie")
                            }.toMutableMap()
                            if (captured.keys.none { it.equals("User-Agent", true) }) {
                                captured["User-Agent"] = browser.settings.userAgentString
                            }
                            if (captured.keys.none { it.equals("Origin", true) }) {
                                AnimeWatchPlayback.publicHttps(source)?.let { captured["Origin"] = "https://${it.host}" }
                            }
                            // Carry only cookies applicable to the media URL, never the page's cookies.
                            CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                                captured["Cookie"] = it
                            }
                            return Stream(url, captured.toMap(), source, AnimeWatchPlayback.isHls(url, type))
                        }

                        browser.addJavascriptInterface(SourceBridge { token, payload ->
                            if (finished.get() || token != nonce) return@SourceBridge
                            val candidate = tryParseJson<AnimeWatchPlayback.PlayerSource>(payload) ?: return@SourceBridge
                            if (candidate.type == "embed_frame") {
                                AnimeWatchPlayback.discoveredFrame(token, nonce, candidate, player)?.let { frame ->
                                    synchronized(pages) { pages.set((pages.get() + frame).distinct().take(4)) }
                                }
                                return@SourceBridge
                            }
                            if (!AnimeWatchPlayback.acceptsMessage(token, nonce, candidate, pages.get())) return@SourceBridge
                            if (candidate.error == "captcha_required") {
                                verificationRequired.set(true)
                                if (!interactive) finish(null)
                                return@SourceBridge
                            }
                            if (!AnimeWatchPlayback.acceptsSource(token, nonce, candidate, pages.get())) return@SourceBridge
                            main.post {
                                if (!finished.get() && continuation.isActive) {
                                    finish(stream(candidate.url, emptyMap(), candidate.referer, candidate.type))
                                }
                            }
                        }, "AnimeWatchBridge")

                        probe = object : Runnable {
                            override fun run() {
                                if (finished.get() || !continuation.isActive) return
                                browser.evaluateJavascript(AnimeWatchPlayback.playerScript) { raw ->
                                    if (finished.get() || !continuation.isActive) return@evaluateJavascript
                                    val json = tryParseJson<String>(raw)
                                    val candidate = json?.let { tryParseJson<AnimeWatchPlayback.PlayerSource>(it) }
                                    if (candidate != null && AnimeWatchPlayback.acceptsSource(nonce, nonce, candidate, pages.get())) {
                                        finish(stream(candidate.url, emptyMap(), candidate.referer, candidate.type))
                                    } else {
                                        main.postDelayed(this, 400L)
                                    }
                                }
                            }
                        }

                        browser.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = AnimeWatchPlayback.publicHttps(request.url.toString()) ?: return true
                                return request.isForMainFrame && !AnimeWatchPlayback.sameOrigin(player, target)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                val target = AnimeWatchPlayback.publicHttps(url) ?: return true
                                return pages.get().none { AnimeWatchPlayback.sameOrigin(it, target) }
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                val target = AnimeWatchPlayback.publicHttps(url) ?: return blocked()
                                if (request.isForMainFrame && !AnimeWatchPlayback.sameOrigin(player, target)) return blocked()
                                if (target.path == "/blocked.html" && pages.get().any { AnimeWatchPlayback.sameOrigin(it, target) }) {
                                    failure.set("Byse: hosting odrzucił dostęp do odtwarzacza (blocked.html).")
                                    finish(null)
                                    return blocked()
                                }
                                val headers = request.requestHeaders.toMap()
                                val source = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                                val sourceOrigin = source?.let(AnimeWatchPlayback::publicHttps)
                                if (request.method == "GET" && AnimeWatchPlayback.isPlayerScript(url, source, pages.get())) {
                                    // Keep the complete parent/iframe handshake. Only augment the
                                    // public entry-script GET; native WebView still handles all POSTs.
                                    return try {
                                        val scriptHeaders = headers.filterKeys { !it.equals("Accept-Encoding", true) }.toMutableMap()
                                        CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                                            scriptHeaders["Cookie"] = it
                                        }
                                        val response = runBlocking { withTimeoutOrNull(15_000L) { app.get(url, headers = scriptHeaders) } }
                                        if (response == null || finished.get()) return null
                                        if (response.code !in 200..299) {
                                            failure.set("Byse: błąd HTTP ${response.code} podczas ładowania skryptu odtwarzacza.")
                                            return null
                                        }
                                        failure.set("Byse: skrypt odtwarzacza został wczytany, ale nie udostępnił źródła wideo w ciągu 45 sekund.")
                                        val script = response.text + "\n" + AnimeWatchPlayback.frameScript(nonce)
                                        WebResourceResponse("application/javascript", "UTF-8", 200, "OK",
                                            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(script.toByteArray(Charsets.UTF_8)))
                                    } catch (_: Exception) {
                                        failure.set("Byse: nie udało się załadować skryptu osadzonego odtwarzacza.")
                                        null
                                    }
                                }
                                if (request.method == "GET" && AnimeWatchPlayback.isMedia(url) && source != null &&
                                    sourceOrigin != null && pages.get().any { AnimeWatchPlayback.sameOrigin(it, sourceOrigin) }) {
                                    main.post {
                                        if (!finished.get() && continuation.isActive) finish(stream(url, headers, source))
                                    }
                                }
                                // Keep native handling of POST bodies, scripts and the host's session.
                                return null
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (url.substringBefore('?').endsWith("/blocked.html")) {
                                    failure.set("Byse: hosting odrzucił dostęp do odtwarzacza (blocked.html).")
                                    finish(null)
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame || AnimeWatchPlayback.isPlayerPage(request.url.toString(), pages.get())) {
                                    failure.set("Byse: błąd HTTP ${response.statusCode} podczas ładowania ramki odtwarzacza.")
                                    finish(null)
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                failure.set("Byse: błąd certyfikatu HTTPS odtwarzacza.")
                                finish(null)
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame || AnimeWatchPlayback.isPlayerPage(request.url.toString(), pages.get())) {
                                    failure.set("Byse: błąd połączenia z ramką odtwarzacza (${error.errorCode}).")
                                    finish(null)
                                }
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                failure.set("Byse: proces Android System WebView został zamknięty.")
                                finish(null)
                                return true
                            }
                        }
                        if (interactive) {
                            val content = FrameLayout(activity).apply {
                                minimumHeight = (activity.resources.displayMetrics.heightPixels * 0.65).toInt()
                                addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                            val window = AlertDialog.Builder(activity)
                                .setTitle("Byse — ręczna weryfikacja")
                                .setMessage("Dokończ weryfikację na stronie. Po uzyskaniu źródła wideo okno zamknie się automatycznie.")
                                .setView(content)
                                .setNegativeButton("Anuluj") { _, _ -> cancelledByUser.set(true); finish(null) }
                                .create()
                            dialog = window
                            window.setOnCancelListener { cancelledByUser.set(true); finish(null) }
                            window.setOnDismissListener { cancelledByUser.set(true); finish(null) }
                            window.show()
                            window.window?.apply {
                                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.92).toInt())
                                clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                            }
                            browser.requestFocus()
                        } else {
                            // No playback attempt can open a dialog. Manual verification is
                            // available only through the plugin's settings action.
                            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                            if (root == null) {
                                failure.set("Byse: brak widoku CloudStream do uruchomienia odtwarzacza.")
                                finish(null)
                                return@open
                            }
                            root.addView(browser, 0, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                        browser.loadUrl(embedUrl, mutableMapOf("Referer" to referer))
                        probe?.let { main.postDelayed(it, 400L) }
                    } catch (error: Exception) {
                        failure.set("Byse: nie udało się uruchomić Android System WebView (${error.javaClass.simpleName}).")
                        finish(null)
                    }
                }
            }
        }
        if (cancelledByUser.get()) throw CancellationException("Anulowano ręczną weryfikację Byse.")
        if (result == null && verificationRequired.get() && !interactive) throw VerificationRequired()
        return result ?: throw ErrorLoadingException(if (interactive) "Byse: nie uzyskano wideo podczas ręcznej weryfikacji."
            else failure.get() + " Możesz otworzyć odtwarzacz ręcznie w ustawieniach AnimeWatch: „Otwórz weryfikację”.")
    }

    private fun blocked() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )
}
