package pl.dedziek.ekinotv

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ClipboardManager
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.net.http.SslCertificate
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.SslErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], shadows = [WebSessionTest.PostingWebView::class])
class WebSessionTest {
    @Implements(WebView::class)
    class PostingWebView : org.robolectric.shadows.ShadowWebView() {
        var post: Pair<String, ByteArray>? = null
        @Implementation fun postUrl(url: String, data: ByteArray) { post = url to data }
    }

    private fun posted(web: WebView) = org.robolectric.shadow.api.Shadow.extract<PostingWebView>(web).post!!
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    private lateinit var state: SessionState
    private lateinit var session: WebSession
    private var now = 0L
    private val mapper = ObjectMapper()

    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        activity = controller.setup().get()
        activity.getSharedPreferences("dedziek_ekino_tv_v1", Context.MODE_PRIVATE).edit().clear().commit()
        state = SessionState(activity) { now }
        session = WebSession(activity, state) { activity }
    }
    @After fun finish() {
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun open(): Pair<Dialog, WebView> {
        session.settings(activity)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(shownAction(options, "Otwórz weryfikację").performClick())
        val dialog = ShadowDialog.getLatestDialog()
        val web = views(dialog.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()
        return dialog to web
    }

    private fun shownAction(dialog: Dialog, label: String): Button {
        shadowOf(Looper.getMainLooper()).idle()
        val button = views(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text.toString() == label }
        assertTrue("Action is not attached: $label", button.isAttachedToWindow)
        assertTrue("Action is hidden: $label", button.isShown)
        button.requestRectangleOnScreen(Rect(0, 0, button.width, button.height), true)
        shadowOf(Looper.getMainLooper()).idle()
        val rect = Rect()
        assertTrue("Action has no visible rectangle: $label", button.getGlobalVisibleRect(rect))
        assertTrue("Action has no touch area: $label", rect.width() > 0 && rect.height() > 0)
        assertTrue(button.isEnabled && button.isFocusable)
        return button
    }

    private fun request(url: String, main: Boolean = false, method: String = "GET") = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = method
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun browser() = views(activity.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()

    @Test fun automaticGateBlocksLateGetAndVerificationPostEvenAfterTheNavigationIsBlanked() = runTest {
        val pending = async(UnconfinedTestDispatcher(testScheduler)) {
            runCatching { session.page("https://ekino.ws/movie/show/test/1") }
        }
        val web = browser()
        reply(web, "https://ekino.ws/movie/show/test/1", 1.0, html = "<title>Just a moment...</title>")
        assertTrue(pending.await().exceptionOrNull() is SiteProblem)
        for (req in listOf(request("https://ekino.ws/watch/f/player/late"),
            request("https://ekino.ws/watch/verify.php", method = "POST"))) {
            assertNotNull("A late request escaped after automatic stop", web.webViewClient.shouldInterceptRequest(web, req))
        }
    }

    @Test fun closingVerificationCannotBeUndoneByALatePageStartedCallback() {
        val (window, web) = open()
        window.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        web.webViewClient.onPageStarted(web, "https://ekino.ws/watch/f/player/late", null)
        assertNotNull(web.webViewClient.shouldInterceptRequest(web,
            request("https://ekino.ws/watch/verify.php", method = "POST")))
    }

    @Test fun aReadCatalogueIsUnloadedAfterIdleAndCannotKeepRequestingInTheBackground() = runTest {
        val page = async(UnconfinedTestDispatcher(testScheduler)) { session.page("https://ekino.ws/movie/cat/") }
        val web = browser()
        reply(web, "https://ekino.ws/movie/cat/", 1.0)
        page.await()
        advanceTimeBy(2_001); runCurrent()
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        assertFalse(state.paused)
        assertNotNull(web.webViewClient.shouldInterceptRequest(web, request("https://ad.example/refresh")))
    }

    private fun controls(id: String) = "<h1 class=title>Film</h1><div class=playerContainer>" +
        "<ul class=players><li><a href='#$id-player'><i title='Lektor PL'></i></a></li></ul>" +
        "<a onclick=\"ShowPlayer('player','$id')\">Oglądaj</a></div>"

    @Test fun cancellingPlayerLoadingRemovesItsVisibleCountdownWithoutPausingTheSession() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val url = "https://ekino.ws/movie/show/test/1"
        val page = async(dispatcher) { session.page(url) }; val web = browser()
        reply(web, url, 1.0, html = controls("id")); page.await()
        val frame = launch(dispatcher) { session.player(url, Player("player", "id", "Lektor PL")) }
        reply(web, url, 1.0, html = controls("id"))
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue("true")
        reply(web, url, 1.0, html = controls("id"), countdown = 9)
        val notice = ShadowDialog.getLatestDialog()
        assertTrue(notice.isShowing)
        frame.cancelAndJoin()
        assertFalse(notice.isShowing)
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        assertFalse(state.paused)
    }

    @Test fun reloadedMaterialResolvesItsCurrentPlayerInsteadOfUsingCachedControlIds() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val url = "https://ekino.ws/movie/show/test/1"
        val old = async(dispatcher) { session.page(url) }; val web = browser()
        reply(web, url, 1.0, html = controls("old-id")); old.await(); session.finishPlayback()
        val frame = async(dispatcher) { session.player(url, Player("player", "old-id", "Lektor PL")) }
        reply(web, url, 2.0, html = controls("fresh-id"))
        reply(web, url, 2.0, html = controls("fresh-id"))
        val script = shadowOf(web).lastEvaluatedJavascript
        assertTrue(script.contains("fresh-id")); assertFalse(script.contains("old-id"))
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue("true")
        val wrapper = WebPage("https://ekino.ws/watch/f/player/fresh-id",
            "<a target=_blank href='https://host.example/e/video'>wideo</a>")
        reply(web, url, 2.0, html = controls("fresh-id"), frames = listOf(wrapper))
        assertEquals(wrapper.url, frame.await().url)
    }

    @Test fun visibleHttp403CanRenderTheChallengeInsteadOfBeingBlanked() {
        val (dialog, web) = open()
        web.webViewClient.onReceivedHttpError(web, request("https://ekino.ws/watch/f/player/id", true),
            WebResourceResponse("text/html", "UTF-8", 403, "Forbidden", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0))))
        assertTrue(state.paused)
        assertNotEquals("about:blank", shadowOf(web).lastLoadedUrl)
        dialog.dismiss()
    }

    @Test fun serverRejectionAlertStopsItsReloadAndBlocksLateWrapperRequestsUntilUserRetry() {
        val (dialog, web) = open()
        @Suppress("UNCHECKED_CAST")
        val receiverClass = Class.forName("android.webkit.JsResult\$ResultReceiver") as Class<Any>
        var completed = false
        val receiver = java.lang.reflect.Proxy.newProxyInstance(receiverClass.classLoader, arrayOf(receiverClass)) { _, _, _ ->
            completed = true; null
        }
        val result = ReflectionHelpers.callConstructor(android.webkit.JsResult::class.java,
            ReflectionHelpers.ClassParameter.from(receiverClass, receiver))
        val wrapper = "https://ekino.ws/watch/f/player/id"
        assertTrue(web.webChromeClient!!.onJsAlert(web, wrapper, "Wystąpił błąd! Spróbuj ponownie.", result))
        assertTrue(shadowOf(result).wasCancelled())
        assertTrue(state.reason.contains("odrzuciło potwierdzenie"))
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        assertEquals(409, web.webViewClient.shouldInterceptRequest(web, request(wrapper))!!.statusCode)
        shownAction(dialog, "Wczytaj ponownie").performClick()
        assertNull(web.webViewClient.shouldInterceptRequest(web, request(wrapper)))
        dialog.dismiss()
    }

    private fun reply(browser: WebView, url: String, id: Double, ready: String = "interactive",
                      html: String = "<h1 class=title>Film</h1><div class=playerContainer></div>",
                      frames: List<WebPage> = emptyList(), countdown: Int? = null) {
        assertEquals(WebSession.SNAPSHOT, shadowOf(browser).lastEvaluatedJavascript)
        val json = mapper.writeValueAsString(mapOf("url" to url, "html" to html, "readyState" to ready,
            "documentId" to id, "hasShowPlayer" to true, "playerCountdown" to countdown,
            "frames" to frames.map { mapOf("url" to it.url, "html" to it.html) }))
        shadowOf(browser).lastEvaluatedJavascriptCallback.onReceiveValue(mapper.writeValueAsString(json))
    }

    @Test
    @Config(sdk = [28, 35], qualifiers = "w411dp-h891dp-port")
    fun pausedSettingsExposeReportAndRecoveryActionsInTheDisplayedWindow() {
        state.remember("https://ekino.ws/movie/show/test/42")
        state.pause("CAPTCHA odtwarzacza", true)
        session.settings(activity)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(Looper.getMainLooper()).idle()
        val shown = views(options.window!!.decorView).filterIsInstance<TextView>()
            .filter { it.isAttachedToWindow && it.isShown }.map { it.text.toString() }
        assertTrue("Displayed text: $shown", shown.contains("Kopiuj raport"))
        assertTrue("Displayed text: $shown", shown.contains("Otwórz stronę / logowanie"))
        for (label in listOf("Otwórz weryfikację", "Otwórz stronę / logowanie", "Kopiuj raport")) shownAction(options, label)
        assertTrue(state.rejection()!!.contains("Otwórz weryfikację"))
        options.dismiss()
    }

    @Test
    @Config(sdk = [28, 35], qualifiers = "w891dp-h411dp-land")
    fun landscapeSettingsKeepAllActionsReachableWithoutAnExistingMaterial() {
        session.settings(activity)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        for (label in listOf("Otwórz weryfikację", "Otwórz stronę / logowanie", "Kopiuj raport")) shownAction(options, label)
        assertTrue(shownAction(options, "Otwórz weryfikację").performClick())
        val window = ShadowDialog.getLatestDialog()
        val web = views(window.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()
        assertEquals("${Site.ENTRY}/", shadowOf(web).lastLoadedUrl)
        window.dismiss()
    }

    @Test fun reportCanBeCopiedThroughTheVisibleButtonWhilePlaybackIsPaused() {
        state.remember("https://ekino.ws/movie/show/test/42")
        state.pause("CAPTCHA odtwarzacza", true)
        val restored = SessionState(activity)
        WebSession(activity, restored) { activity }.settings(activity)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(shownAction(options, "Kopiuj raport").performClick())
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val report = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(report.contains("Ekino TV v8"))
        assertTrue(report.contains("CAPTCHA odtwarzacza"))
        assertTrue(restored.paused)
        assertTrue(views(activity.findViewById(android.R.id.content)).none { it is WebView })
        options.dismiss()
    }

    @Test fun theVisibleVerificationActionOpensTheRememberedFilmAndReleasesPauseOnlyForAReadyPlayer() {
        val material = "https://ekino.ws/movie/show/test/42"
        state.remember(material); state.pause("CAPTCHA odtwarzacza", true)
        val (window, browser) = open()
        assertEquals(material, shadowOf(browser).lastLoadedUrl)
        assertTrue(shownAction(window, "Sprawdź i zamknij").performClick())
        val wrapperUrl = "https://ekino.ws/watch/f/player/id"
        reply(browser, material, 1.0, frames = listOf(WebPage(wrapperUrl, "<div class=cf-turnstile></div>")))
        assertTrue(window.isShowing); assertTrue(state.paused)
        assertTrue(shownAction(window, "Sprawdź i zamknij").performClick())
        reply(browser, material, 1.0, frames = listOf(WebPage(wrapperUrl,
            "<a target=_blank href='https://host.example/e/id'>wideo</a>")))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(window.isShowing); assertFalse(state.paused)
        assertFalse(SessionState(activity).paused)
        assertEquals("about:blank", shadowOf(browser).lastLoadedUrl)
        assertTrue(session.operations.tryLock()); session.operations.unlock()
    }

    @Test fun verificationReusesTheSameBrowserAndKeepsItsOwnIdentityAndCookies() {
        val (first, browser) = open()
        assertTrue(browser.settings.javaScriptEnabled)
        assertTrue(browser.settings.domStorageEnabled)
        assertFalse(browser.settings.allowFileAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, browser.settings.mixedContentMode)
        val agent = browser.settings.userAgentString
        CookieManager.getInstance().setCookie("https://ekino.ws/", "PHPSESSID=private; Path=/; Secure")
        first.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("about:blank", shadowOf(browser).lastLoadedUrl)
        val (second, same) = open()
        assertSame(browser, same)
        assertEquals(agent, same.settings.userAgentString)
        assertTrue(CookieManager.getInstance().getCookie("https://ekino.ws/").contains("PHPSESSID=private"))
        second.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(session.operations.tryLock()); session.operations.unlock()
    }

    @Test fun aManualHttp429StopsTimersPersistsPauseAndNeverEnablesAnAutomaticRetry() {
        val (dialog, browser) = open()
        browser.webViewClient.onReceivedHttpError(browser, request("https://ekino.ws/watch/f/player/id"),
            WebResourceResponse("text/plain", "UTF-8", 429, "Too Many Requests", emptyMap(), "denied".byteInputStream()))
        assertTrue(state.paused); assertTrue(state.playerRequired)
        assertTrue(SessionState(activity).paused)
        assertEquals("about:blank", shadowOf(browser).lastLoadedUrl)
        dialog.dismiss()
        assertTrue(state.paused)
    }

    @Test fun repeatedWrapperRequestsAreStoppedEvenWhenTheWrapperIsAnIframe() {
        val (dialog, browser) = open()
        val wrapper = request("https://ekino.ws/watch/f/player/id?private=SECRET")
        repeat(3) { assertNull(browser.webViewClient.shouldInterceptRequest(browser, wrapper)) }
        assertNotNull(browser.webViewClient.shouldInterceptRequest(browser, wrapper))
        assertTrue(state.paused)
        assertEquals("about:blank", shadowOf(browser).lastLoadedUrl)
        assertFalse(state.report().contains("SECRET"))
        dialog.dismiss()
    }

    @Test fun externalTopLevelNavigationCannotReplaceTheVerificationPage() {
        val (dialog, browser) = open()
        assertTrue(browser.webViewClient.shouldOverrideUrlLoading(browser, request("https://ad.example/", true)))
        assertFalse(browser.webViewClient.shouldOverrideUrlLoading(browser, request("https://ekino.ws/", true)))
        assertFalse(browser.webViewClient.shouldOverrideUrlLoading(browser, request("https://challenges.cloudflare.com/frame", false)))
        dialog.dismiss()
    }

    @Test fun cancellingSearchOrLeavingTheScreenDoesNotCreateAVerificationPause() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { session.page("${Site.ENTRY}/") }
        job.cancelAndJoin()
        assertFalse(state.paused)
        val browser = views(activity.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()
        assertEquals("about:blank", shadowOf(browser).lastLoadedUrl)
    }

    @Test fun threeFilmsCanLoadInSequenceWithoutWaitingForPageFinishedOrOldCallbacks() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var previous = "https://ekino.ws/movie/show/old/1"
        for (index in 1..3) {
            val material = "https://ekino.ws/movie/show/movie$index/${index + 10}"
            val page = async(dispatcher) { session.page(material) }
            val web = browser()
            if (index > 1) {
                // The previous document's completion cannot finish, reset or poison this navigation.
                web.webViewClient.onPageStarted(web, previous, null)
                web.webViewClient.onPageFinished(web, previous)
                reply(web, previous, index - 1.0)
                assertFalse(page.isCompleted)
                advanceTimeBy(200); runCurrent()
            }
            reply(web, material, index.toDouble(), html = controls("id$index"))
            assertEquals(material, page.await().url)
            val player = Player("player", "id$index", "Lektor PL")
            val frame = async(dispatcher) { session.player(material, player) }
            reply(web, material, index.toDouble(), html = controls("id$index"))
            assertTrue(shadowOf(web).lastEvaluatedJavascript.contains("window.ShowPlayer"))
            shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue("true")
            val wrapper = WebPage("https://ekino.ws/watch/f/${player.identity}",
                "<a target=_blank href='https://host.example/e/id$index'>wideo</a>")
            reply(web, material, index.toDouble(), frames = listOf(wrapper))
            assertEquals(wrapper.url, frame.await().url)
            session.finishPlayback()
            assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
            assertFalse(state.paused); assertNull(state.rejection())
            previous = material
        }
    }

    @Test fun ownDeadlineFailsOneOperationAndAllowsTheNextFilmAfterTheShortCooldown() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val failed = async(dispatcher) { runCatching { session.page("https://ekino.ws/movie/show/slow/1") } }
        val web = browser()
        advanceTimeBy(20_001); runCurrent()
        val error = failed.await().exceptionOrNull()
        assertTrue(error is SiteProblem)
        assertTrue(error!!.message!!.contains("materiał"))
        assertFalse(error.message!!.contains("Sprawdź i zamknij"))
        assertFalse(state.paused); assertFalse(SessionState(activity).paused)
        assertNotNull(state.rejection())
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        now += 10_000
        assertNull(state.rejection())
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl) // Expiry does not reload anything.
        val next = "https://ekino.ws/movie/show/next/2"
        val success = async(dispatcher) { session.page(next) }
        reply(web, next, 2.0)
        assertEquals(next, success.await().url)
    }

    @Test fun cloudStreamDeadlineIsCancellationAndNeverTurnsIntoAPersistentOrTemporaryPause() = runTest {
        val failed = async(UnconfinedTestDispatcher(testScheduler)) {
            runCatching { withTimeout(1000) { session.page("https://ekino.ws/movie/show/test/1") } }
        }
        val web = browser()
        advanceTimeBy(1001); runCurrent()
        assertTrue(failed.await().exceptionOrNull() is TimeoutCancellationException)
        assertFalse(state.paused); assertNull(state.rejection())
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        // A late JS callback from the cancelled request cannot create a new CAPTCHA pause.
        reply(web, "https://ekino.ws/movie/show/test/1", 1.0, html = "<title>Just a moment...</title>")
        assertFalse(state.paused)
    }

    @Test fun actualCaptchaIsDetectedBeforePageFinishedAndStillStopsTheNextFilm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val result = async(dispatcher) { runCatching { session.page("https://ekino.ws/movie/show/test/1") } }
        val web = browser()
        reply(web, "https://ekino.ws/movie/show/test/1", 1.0, ready = "loading", html = "<title>Just a moment...</title>")
        assertTrue(result.await().exceptionOrNull() is SiteProblem)
        assertTrue(state.paused); assertTrue(SessionState(activity).paused)
        val blocked = runCatching { session.page("https://ekino.ws/movie/show/next/2") }
        assertTrue(blocked.exceptionOrNull() is SiteProblem)
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
    }

    @Test fun aFinishedDocumentsLateHttpErrorCannotPoisonTheNextFilm() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = "https://ekino.ws/movie/show/first/1"
        val page = async(dispatcher) { session.page(first) }; val web = browser()
        reply(web, first, 1.0); page.await(); session.finishPlayback()
        web.webViewClient.onReceivedHttpError(web, request(first, true),
            WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "denied".byteInputStream()))
        assertFalse(state.paused); assertNull(state.rejection())
        val next = "https://ekino.ws/movie/show/second/2"
        val result = async(dispatcher) { session.page(next) }
        reply(web, next, 2.0)
        assertEquals(next, result.await().url)
    }

    @Test fun anOldHttpErrorDuringTheNextNavigationCannotUseWebViewsStaleCurrentUrl() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = "https://ekino.ws/movie/show/first/1"
        val initial = async(dispatcher) { session.page(first) }; val web = browser()
        reply(web, first, 1.0); initial.await(); session.finishPlayback()
        val next = "https://ekino.ws/movie/show/second/2"
        val loading = async(dispatcher) { session.page(next) }
        // Android documents that getUrl() may still expose A after navigation to B has begun.
        ReflectionHelpers.setField(shadowOf(web), "originalUrl", first)
        assertEquals(first, web.url)
        web.webViewClient.onReceivedHttpError(web, request(first, true),
            WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "old denial".byteInputStream()))
        assertFalse(state.paused); assertNull(state.rejection())
        reply(web, next, 2.0)
        assertEquals(next, loading.await().url)
        // A denial for B must still be respected, even while getUrl() remains stale.
        web.webViewClient.onReceivedHttpError(web, request(next, true),
            WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "new denial".byteInputStream()))
        assertTrue(state.paused)
    }

    @Test fun anOldCertificateErrorCannotInterruptTheNextMovieButItsCertificateIsRejected() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = "https://ekino.ws/movie/show/first/1"
        val initial = async(dispatcher) { session.page(first) }; val web = browser()
        reply(web, first, 1.0); initial.await(); session.finishPlayback()
        val next = "https://ekino.ws/movie/show/second/2"
        val loading = async(dispatcher) { session.page(next) }
        val handler = ReflectionHelpers.callConstructor(SslErrorHandler::class.java)
        val certificate = org.robolectric.shadow.api.Shadow.newInstanceOf(SslCertificate::class.java)
        web.webViewClient.onReceivedSslError(web, handler, SslError(SslError.SSL_DATE_INVALID, certificate, first))
        assertTrue(shadowOf(handler).wasCancelCalled())
        assertFalse(shadowOf(handler).wasProceedCalled())
        assertFalse(state.paused); assertNull(state.rejection())
        reply(web, next, 2.0)
        assertEquals(next, loading.await().url)
        val current = ReflectionHelpers.callConstructor(SslErrorHandler::class.java)
        web.webViewClient.onReceivedSslError(web, current, SslError(SslError.SSL_DATE_INVALID, certificate, next))
        assertTrue(shadowOf(current).wasCancelCalled())
        assertFalse(state.paused); assertNotNull(state.rejection())
    }

    @Test fun searchAfterPlaybackPostsInTheEstablishedBrowserWithoutAnotherHomepageGetOrStaleResults() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = async(dispatcher) { session.page("https://ekino.ws/movie/show/first/1") }
        val web = browser(); reply(web, "https://ekino.ws/movie/show/first/1", 1.0); first.await()
        session.finishPlayback()
        val search = async(dispatcher) { session.search("Zabójcza") }
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        assertEquals("https://ekino.ws/search/qf", posted(web).first)
        assertEquals("q=Zab%C3%B3jcza", posted(web).second.toString(Charsets.UTF_8))
        reply(web, "https://ekino.ws/movie/show/first/1", 1.0)
        assertFalse(search.isCompleted)
        advanceTimeBy(200); runCurrent()
        val url = "https://ekino.ws/search/qf/proba"
        reply(web, url, 3.0)
        assertEquals(url, search.await().url)

        val second = async(dispatcher) { session.search("Powrót") }
        assertEquals("q=Powr%C3%B3t", posted(web).second.toString(Charsets.UTF_8))
        reply(web, url, 3.0) // Same search path, but this is still the old document.
        assertFalse(second.isCompleted)
        advanceTimeBy(200); runCurrent()
        reply(web, url, 4.0)
        assertEquals(url, second.await().url)
        assertNull(state.rejection())
    }

    @Test fun aColdSearchEstablishesTheSiteOnceAndPostsEncodedFormDataWithoutRunningAFormScript() = runTest {
        val search = async(UnconfinedTestDispatcher(testScheduler)) { session.search("A&B + ") }
        val web = browser()
        assertEquals("${Site.ENTRY}/", shadowOf(web).lastLoadedUrl)
        reply(web, "https://ekino.ws/", 1.0)
        assertEquals("https://ekino.ws/search/qf", posted(web).first)
        assertEquals("q=A%26B+%2B+", posted(web).second.toString(Charsets.UTF_8))
        reply(web, "https://ekino.ws/search/qf/result", 2.0)
        assertEquals("https://ekino.ws/search/qf/result", search.await().url)
    }

    @Test fun activityStoppingCancelsThePendingOperationAndDisallowsAllLateRequests() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { session.page("${Site.ENTRY}/") }
        val web = browser()
        ReflectionHelpers.callInstanceMethod<Unit>(activity.application, "dispatchActivityStopped",
            ReflectionHelpers.ClassParameter.from(Activity::class.java, activity))
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(state.paused)
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        assertNotNull(web.webViewClient.shouldInterceptRequest(web, request("https://ekino.ws/")))
    }

    @Test fun anExplicitNewNavigationReopensTheNetworkAfterIdleWithoutRejectingChallengeResources() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = async(dispatcher) { session.page("${Site.ENTRY}/") }
        val web = browser(); reply(web, "https://ekino.ws/", 1.0); first.await()
        advanceTimeBy(2_001); runCurrent()
        assertNotNull(web.webViewClient.shouldInterceptRequest(web, request("https://ekino.ws/late")))
        val next = async(dispatcher) { session.page("https://ekino.ws/movie/show/next/2") }
        assertNull(web.webViewClient.shouldInterceptRequest(web, request("https://ekino.ws/movie/show/next/2", true)))
        assertNull(web.webViewClient.shouldInterceptRequest(web, request("https://challenges.cloudflare.com/frame")))
        reply(web, "https://ekino.ws/movie/show/next/2", 2.0)
        next.await()
        assertTrue(state.report().contains("zatrzymane=1"))
    }
}
