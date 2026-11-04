package pl.filmancloudstream

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
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream

object FilmanLoginUi {
    fun show(context: Context, session: FilmanSession) {
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
            "Brak zapisanej sesji. Zaloguj się samodzielnie na stronie Filman, a następnie wybierz „Zapisz sesję”."
        }
        AlertDialog.Builder(activity)
            .setTitle("Filman — konto darmowe")
            .setMessage("$info\n\nDodatek przechowuje sesję lokalnie. Hasło wpisujesz wyłącznie w formularzu strony. Sesja nie jest przenoszona w kopii CloudStream.")
            .setPositiveButton("Otwórz logowanie") { _, _ -> openLogin(activity, session) }
            .setNeutralButton("Usuń zapisaną sesję") { _, _ ->
                session.clear()
                toast(activity, "Usunięto zapisaną sesję Filmana na tym urządzeniu.")
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun openLogin(activity: Activity, session: FilmanSession) {
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
                if (request.isForMainFrame && !FilmanSession.isFilmanUrl(request.url.toString())) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(),
                        ByteArrayInputStream("Zewnętrzna nawigacja jest wyłączona.".toByteArray(Charsets.UTF_8)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.isForMainFrame && blockExternal(request.url.toString())
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (FilmanLoginPolicy.allowLegacyNavigation(url)) return false
                return blockExternal(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageError = null
                status.text = "Ładowanie strony logowania…"
            }

            override fun onPageFinished(view: WebView, url: String) {
                status.text = pageError ?: "Jeżeli strona prosi o weryfikację, dokończ ją samodzielnie. Po zalogowaniu wybierz „Zapisz sesję”."
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame) return
                pageError = when (response.statusCode) {
                    403 -> "Strona odmówiła dostępu (HTTP 403). Jeśli Cloudflare wyświetla „Sorry, you have been blocked”, sprawdź ten sam adres w zwykłej przeglądarce. Zapis sesji nie usuwa blokady serwisu."
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
                if (error?.url?.let(FilmanSession::isFilmanUrl) == true) {
                    pageError = "Błąd certyfikatu HTTPS Filmana. Nie można bezpiecznie otworzyć logowania."
                    status.text = pageError
                }
            }

            private fun blockExternal(url: String): Boolean {
                if (FilmanSession.isFilmanUrl(url)) return false
                toast(activity, "Zewnętrzny adres został pominięty. Pozostań na stronie filman.cc.")
                return true
            }
            // Default WebView certificate and Safe Browsing handling is retained.
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = "Zaloguj się w formularzu Filmana. Sam zapis ciasteczek nie potwierdza poprawnego logowania."
                setPadding(padding, padding / 2, padding, padding / 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Logowanie — filman.cc")
            .setView(content)
            .setPositiveButton("Zapisz sesję", null)
            .setNeutralButton("Odśwież", null)
            .setNegativeButton("Zamknij", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.92).toInt())
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val currentUrl = web.url.orEmpty()
                if (!FilmanSession.isFilmanUrl(currentUrl)) {
                    toast(activity, "Otwórz stronę Filmana przed zapisaniem sesji.")
                    return@setOnClickListener
                }
                val manager = CookieManager.getInstance()
                val cookies = manager.getCookie(FilmanSession.BASE_URL).orEmpty()
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
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (FilmanSession.isFilmanUrl(web.url.orEmpty())) web.reload()
                else web.loadUrl("${FilmanSession.BASE_URL}/logowanie")
            }
            web.requestFocus()
            web.loadUrl("${FilmanSession.BASE_URL}/logowanie")
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
