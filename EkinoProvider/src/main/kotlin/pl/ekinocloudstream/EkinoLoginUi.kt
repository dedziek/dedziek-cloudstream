package pl.ekinocloudstream

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream

object EkinoLoginUi {
    fun show(context: Context, session: EkinoSession) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(context, session) }
            return
        }
        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            toast(context, "Otwórz ustawienia dodatku na ekranie CloudStream.")
            return
        }
        val info = if (session.hasSession()) {
            "Na tym urządzeniu jest zapisana sesja. Jeżeli wyszukiwanie prosi o logowanie, zaloguj się ponownie."
        } else {
            "Katalog działa bez konta. Jeśli odtwarzacz wymaga logowania, zaloguj się na stronie i wybierz „Zapisz sesję”."
        }
        AlertDialog.Builder(activity)
            .setTitle("Ekino — logowanie opcjonalne")
            .setMessage("$info\n\nDodatek przechowuje sesję lokalnie. Hasło wpisujesz wyłącznie w formularzu strony. Sesja nie jest przenoszona w kopii CloudStream.")
            .setPositiveButton("Otwórz logowanie") { _, _ -> openLogin(activity, session) }
            .setNeutralButton("Usuń zapisaną sesję") { _, _ ->
                session.clear()
                toast(activity, "Usunięto zapisaną sesję Ekino na tym urządzeniu.")
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun openLogin(activity: Activity, session: EkinoSession) {
        val web = try { WebView(activity) } catch (_: Exception) {
            toast(activity, "Nie można uruchomić Android System WebView. Sprawdź jego instalację i aktualizacje.")
            return
        }
        // Retain the actual WebView identity for the entire verification/login session.
        val userAgent = web.settings.userAgentString
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
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
            web.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        // Turnstile's verification frames need cookies in this login WebView.
        manager.setAcceptThirdPartyCookies(web, true)
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val status = TextView(activity).apply {
            text = "Ładowanie strony logowania…"
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        var pageError: String? = null
        web.setDownloadListener { _, _, _, _, _ -> toast(activity, "To okno służy wyłącznie do logowania.") }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // shouldOverrideUrlLoading is not invoked for POST form submissions.
                if (request.isForMainFrame && !EkinoSession.isEkinoUrl(request.url.toString()) && !EkinoLoginPolicy.isBlank(request.url.toString())) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(),
                        ByteArrayInputStream("Zewnętrzna nawigacja jest wyłączona.".toByteArray(Charsets.UTF_8)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.isForMainFrame && blockExternal(request.url.toString())
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (EkinoLoginPolicy.allowLegacyNavigation(url)) return false
                return blockExternal(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageError = null
                status.text = "Ładowanie strony logowania…"
            }

            override fun onPageFinished(view: WebView, url: String) {
                status.text = pageError ?: "Jeśli weryfikacja się zapętla, sprawdź aktualizacje Android System WebView i spróbuj innej sieci (Wi-Fi / dane komórkowe). Po zalogowaniu wybierz „Zapisz sesję”."
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame) return
                pageError = when (response.statusCode) {
                    403 -> "HTTP 403. Jeśli Cloudflare nie przechodzi, zmień internet: Wi-Fi / dane komórkowe."
                    429 -> "Strona ograniczyła liczbę prób (HTTP 429). Odczekaj przed kolejną próbą."
                    else -> "Nie udało się otworzyć strony (HTTP ${response.statusCode})."
                }
                status.text = pageError
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                pageError = "Błąd połączenia ze stroną logowania (${error.errorCode})."
                status.text = pageError
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                if (error?.url?.let(EkinoSession::isEkinoUrl) == true) {
                    pageError = "Błąd certyfikatu HTTPS Ekino. Nie można bezpiecznie otworzyć logowania."
                    status.text = pageError
                }
            }

            private fun blockExternal(url: String): Boolean {
                if (EkinoSession.isEkinoUrl(url) || EkinoLoginPolicy.isBlank(url)) return false
                toast(activity, "Zewnętrzny adres został pominięty. Pozostań na stronie ekino.ws.")
                return true
            }
            // Default WebView certificate and Safe Browsing handling is retained.
        }
        val dialog = EkinoLoginDialog(activity, web, status)
        dialog.setOnShowListener {
            dialog.saveButton.setOnClickListener {
                val currentUrl = web.url.orEmpty()
                if (!EkinoSession.isEkinoUrl(currentUrl)) {
                    toast(activity, "Otwórz stronę Ekino przed zapisaniem sesji.")
                    return@setOnClickListener
                }
                val manager = CookieManager.getInstance()
                val cookies = manager.getCookie(EkinoSession.BASE_URL).orEmpty()
                if (cookies.isBlank()) {
                    toast(activity, "Brak ciasteczek sesji. Dokończ logowanie na stronie i spróbuj ponownie.")
                    return@setOnClickListener
                }
                try {
                    manager.flush()
                    session.save(cookies, userAgent)
                    toast(activity, "Zapisano sesję. Spróbuj wyszukiwania w dodatku.")
                    dialog.dismiss()
                } catch (_: Exception) {
                    toast(activity, "Nie udało się zapisać sesji na urządzeniu. Spróbuj ponownie.")
                }
            }
            dialog.refreshButton.setOnClickListener {
                if (EkinoSession.isEkinoUrl(web.url.orEmpty())) web.reload()
                else web.loadUrl("${EkinoSession.BASE_URL}/login")
            }
            web.requestFocus()
            web.loadUrl("${EkinoSession.BASE_URL}/login")
        }
        dialog.setOnDismissListener {
            web.stopLoading()
            web.clearFormData()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        dialog.show()
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context = context
        val seen = mutableSetOf<Context>()
        while (seen.add(current)) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
