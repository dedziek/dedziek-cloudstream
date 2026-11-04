package pl.animewatchcloudstream

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Lets the hosting's own scripts initialise the player without opening a dialog. */
object AnimeWatchBrowser {
    data class Stream(
        val url: String,
        val headers: Map<String, String>,
        val referer: String,
        val isHls: Boolean
    )

    data class PlayerSource(val url: String = "", val type: String = "", val referer: String = "")

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    suspend fun resolve(embedUrl: String, referer: String): Stream? {
        val player = AnimeWatchPlayback.publicHttps(embedUrl) ?: return null
        if (AnimeWatchPlayback.publicHttps(referer) == null) return null
        return withTimeoutOrNull(45_000L) {
            suspendCancellableCoroutine { continuation ->
                val main = Handler(Looper.getMainLooper())
                val finished = AtomicBoolean(false)
                var web: WebView? = null
                var probe: Runnable? = null

                fun finish(result: Stream?) {
                    if (!finished.compareAndSet(false, true)) return
                    main.post {
                        probe?.let(main::removeCallbacks)
                        probe = null
                        web?.let { view ->
                            runCatching { view.stopLoading() }
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
                        finish(null)
                        return@open
                    }
                    try {
                        val browser = WebView(activity)
                        web = browser
                        browser.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
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
                        browser.alpha = 0f
                        browser.isFocusable = false
                        browser.isFocusableInTouchMode = false
                        browser.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            browser.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        }
                        browser.webChromeClient = WebChromeClient()
                        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
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
                            // Carry only cookies applicable to the media URL, never the page's cookies.
                            CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                                captured["Cookie"] = it
                            }
                            return Stream(url, captured.toMap(), source, AnimeWatchPlayback.isHls(url, type))
                        }

                        probe = object : Runnable {
                            override fun run() {
                                if (finished.get() || !continuation.isActive) return
                                browser.evaluateJavascript(AnimeWatchPlayback.playerScript) { raw ->
                                    if (finished.get() || !continuation.isActive) return@evaluateJavascript
                                    val json = tryParseJson<String>(raw)
                                    val candidate = json?.let { tryParseJson<PlayerSource>(it) }
                                    val source = candidate?.referer?.let(AnimeWatchPlayback::publicHttps)
                                    if (candidate != null && AnimeWatchPlayback.publicHttps(candidate.url) != null &&
                                        source != null && AnimeWatchPlayback.sameOrigin(player, source)) {
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
                                return !AnimeWatchPlayback.sameOrigin(player, target)
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                val target = AnimeWatchPlayback.publicHttps(url) ?: return blocked()
                                if (request.isForMainFrame && !AnimeWatchPlayback.sameOrigin(player, target)) return blocked()
                                val headers = request.requestHeaders.toMap()
                                val source = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                                val sourceOrigin = source?.let(AnimeWatchPlayback::publicHttps)
                                if (request.method == "GET" && AnimeWatchPlayback.isMedia(url) && source != null &&
                                    sourceOrigin != null && AnimeWatchPlayback.sameOrigin(player, sourceOrigin)) {
                                    main.post {
                                        if (!finished.get() && continuation.isActive) finish(stream(url, headers, source))
                                    }
                                }
                                // Keep native handling of POST bodies, scripts and the host's session.
                                return null
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (url.substringBefore('?').endsWith("/blocked.html")) finish(null)
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                finish(null)
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) finish(null)
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                finish(null)
                                return true
                            }
                        }
                        // Attach behind the app's content so layout-dependent players can initialise.
                        val root = activity.findViewById<ViewGroup>(android.R.id.content)
                        if (root == null) {
                            finish(null)
                            return@open
                        }
                        root.addView(browser, 0, ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                        browser.loadUrl(embedUrl, mapOf("Referer" to referer))
                        probe?.let { main.postDelayed(it, 400L) }
                    } catch (_: Exception) {
                        finish(null)
                    }
                }
            }
        }
    }

    private fun blocked() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )
}
