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
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Opens the ordinary embedded player; the user handles playback and any site verification. */
object AnimeWatchBrowser {
    data class Stream(
        val url: String,
        val headers: Map<String, String>,
        val userAgent: String?,
        val referer: String?
    )

    private val mediaPath = Regex("""(?i)\.(?:m3u8|mp4)$""")
    private val publicHost = Regex("""(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}$""")
    private val localSuffixes = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".test", ".invalid")

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    suspend fun resolve(embedUrl: String, referer: String): Stream? {
        val player = publicHttps(embedUrl) ?: return null
        if (publicHttps(referer) == null) return null
        return withTimeoutOrNull(180_000L) {
            suspendCancellableCoroutine { continuation ->
                val main = Handler(Looper.getMainLooper())
                val finished = AtomicBoolean(false)
                var dialog: AlertDialog? = null
                var web: WebView? = null

                fun finish(result: Stream?) {
                    if (!finished.compareAndSet(false, true)) return
                    main.post {
                        dialog?.setOnDismissListener(null)
                        dialog?.setOnCancelListener(null)
                        runCatching { dialog?.dismiss() }
                        dialog = null
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            browser.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        }
                        browser.webChromeClient = WebChromeClient()
                        // Keep the browser's normal first-party cookie policy. Do not copy AnimeWatch cookies.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
                        browser.setDownloadListener { _, _, _, _, _ -> }
                        browser.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) = Unit
                            override fun onViewDetachedFromWindow(view: View) { finish(null) }
                        })
                        browser.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = publicHttps(request.url.toString()) ?: return true
                                return request.isForMainFrame && !sameOrigin(player, target)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                val target = publicHttps(url) ?: return true
                                return !sameOrigin(player, target)
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                val target = publicHttps(url) ?: return blocked()
                                if (request.isForMainFrame && !sameOrigin(player, target)) return blocked()
                                val headers = request.requestHeaders.toMap()
                                val source = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                                val sourceOrigin = source?.let(::publicHttps)
                                // A cross-origin advertisement iframe must not become the selected movie.
                                if (request.method == "GET" && mediaPath.containsMatchIn(target.path.orEmpty()) &&
                                    sourceOrigin != null && sameOrigin(player, sourceOrigin)) {
                                    main.post capture@{
                                        if (finished.get() || !continuation.isActive) return@capture
                                        val captured = headers.filterKeys {
                                            it.lowercase() !in setOf("host", "connection", "content-length", "accept-encoding", "range")
                                        }.toMutableMap()
                                        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                                            ?: browser.settings.userAgentString
                                        if (captured.keys.none { it.equals("User-Agent", true) }) captured["User-Agent"] = ua
                                        if (captured.keys.none { it.equals("Cookie", true) }) {
                                            // Only cookies applicable to this exact captured media URL are returned.
                                            CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                                                captured["Cookie"] = it
                                            }
                                        }
                                        finish(Stream(url, captured.toMap(), ua, source))
                                    }
                                }
                                // Native WebView handles requests, POST bodies, scripts and site verification.
                                return null
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                Toast.makeText(activity, "Odtwarzacz zgłosił błąd certyfikatu HTTPS.", Toast.LENGTH_LONG).show()
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
                        val content = FrameLayout(activity).apply {
                            minimumHeight = (activity.resources.displayMetrics.heightPixels * 0.60).toInt()
                            addView(browser, FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                        val window = AlertDialog.Builder(activity)
                            .setTitle("AnimeWatch — uruchom odtwarzacz")
                            .setMessage("Naciśnij odtwarzanie na stronie. Jeśli odtwarzacz wymaga weryfikacji, wykonaj ją samodzielnie. Po wykryciu filmu okno zamknie się automatycznie.")
                            .setView(content)
                            .setNegativeButton("Anuluj") { _, _ -> finish(null) }
                            .create()
                        dialog = window
                        window.setOnCancelListener { finish(null) }
                        window.setOnDismissListener { finish(null) }
                        window.show()
                        window.window?.apply {
                            setLayout(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                (activity.resources.displayMetrics.heightPixels * 0.92).toInt()
                            )
                            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                        }
                        browser.requestFocus()
                        browser.loadUrl(embedUrl, mapOf("Referer" to referer))
                    } catch (_: Exception) {
                        finish(null)
                    }
                }
            }
        }
    }

    private fun publicHttps(url: String): URI? = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            host.length > 253 || !publicHost.matches(host) || localSuffixes.any(host::endsWith)) return null
        uri
    }.getOrNull()

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.host.equals(second.host, ignoreCase = true)

    private fun blocked() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )
}
