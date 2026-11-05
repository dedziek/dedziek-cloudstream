package pl.ekinocloudstream

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.ClipboardManager
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
        AlertDialog.Builder(activity)
            .setTitle("Ekino — ustawienia")
            .setItems(arrayOf("Otwórz logowanie", "Otwórz materiał do weryfikacji", "Wznów żądania po przerwie", "Usuń zapisaną sesję", "Raport ostatniego odtwarzania", "Raport ostatniego połączenia")) { _, which ->
                when (which) {
                    0 -> openBrowser(activity, session)
                    1 -> {
                        val page = session.verificationPage()?.let(EkinoLoginPolicy::verificationPage)
                        if (page == null) toast(activity, "Najpierw uruchom materiał, który wymaga CAPTCHA. Po restarcie aplikacji wykonaj tę próbę ponownie.")
                        else openBrowser(activity, session, page)
                    }
                    2 -> {
                        try {
                            session.requestGate.resume()
                            toast(activity, "Wznowiono żądania. Uruchom materiał ręcznie; połączenie z serwisem może nadal nie działać.")
                        } catch (_: Exception) { toast(activity, "Nie udało się wznowić żądań Ekino.") }
                    }
                    3 -> {
                        session.clear()
                        toast(activity, "Usunięto zapisaną sesję Ekino na tym urządzeniu.")
                    }
                    4 -> showReport(activity, session.lastPlaybackReport, "Ekino — raport odtwarzania")
                    5 -> showReport(activity, session.networkJournal.report(), "Ekino — raport połączenia")
                }
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun showReport(activity: Activity, report: String?, title: String) {
        if (report == null) { toast(activity, "Brak raportu w tej sesji aplikacji."); return }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(report)
            .setPositiveButton("Kopiuj") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Ekino", report))
                toast(activity, "Skopiowano raport Ekino.")
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun openBrowser(activity: Activity, session: EkinoSession, verificationPage: String? = null) {
        val verification = verificationPage != null
        val startUrl = if (verificationPage != null) EkinoLoginPolicy.verificationPage(verificationPage) ?: return
            else "${EkinoSession.BASE_URL}/login"
        val loadingText = if (verification) "Ładowanie materiału do ręcznej weryfikacji…" else "Ładowanie strony logowania…"
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
            mediaPlaybackRequiresUserGesture = true
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
            text = loadingText
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        var pageError: String? = null
        web.setDownloadListener { _, _, _, _, _ -> toast(activity, "Pobieranie plików w tym oknie jest wyłączone.") }
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
                status.text = loadingText
            }

            override fun onPageFinished(view: WebView, url: String) {
                status.text = pageError ?: if (verification)
                    "Wybierz ten sam odtwarzacz i wykonaj weryfikację. Potem wybierz „Zapisz sesję” i ponownie uruchom materiał w dodatku."
                    else "Jeśli weryfikacja się zapętla, sprawdź aktualizacje Android System WebView i spróbuj innej sieci (Wi-Fi / dane komórkowe). Po zalogowaniu wybierz „Zapisz sesję”."
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
                pageError = "Błąd połączenia ze stroną Ekino (${error.errorCode})."
                status.text = pageError
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                if (error?.url?.let(EkinoSession::isEkinoUrl) == true) {
                    pageError = "Błąd certyfikatu HTTPS Ekino. Nie można bezpiecznie otworzyć strony."
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
        val dialog = EkinoLoginDialog(activity, web, status, verification)
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
                    session.requestGate.resume()
                    toast(activity, if (verification) "Zapisano sesję. Uruchom materiał ponownie; sam zapis nie potwierdza przejścia CAPTCHA."
                        else "Zapisano sesję. Spróbuj wyszukiwania w dodatku.")
                    dialog.dismiss()
                } catch (_: Exception) {
                    toast(activity, "Nie udało się zapisać sesji na urządzeniu. Spróbuj ponownie.")
                }
            }
            dialog.refreshButton.setOnClickListener {
                if (EkinoSession.isEkinoUrl(web.url.orEmpty())) web.reload()
                else web.loadUrl(startUrl)
            }
            web.requestFocus()
            web.loadUrl(startUrl)
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
