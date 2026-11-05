package pl.dedziek.ekinotv

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WebSessionTest {
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
        options.listView.performItemClick(options.listView.getChildAt(0), 0, 0)
        options.dismiss()
        val dialog = ShadowDialog.getLatestDialog()
        val web = views(dialog.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()
        return dialog to web
    }

    private fun request(url: String, main: Boolean = false) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun browser() = views(activity.findViewById(android.R.id.content)).filterIsInstance<WebView>().single()

    private fun reply(browser: WebView, url: String, id: Double, ready: String = "interactive",
                      html: String = "<h1 class=title>Film</h1><div class=playerContainer></div>",
                      frames: List<WebPage> = emptyList()) {
        assertEquals(WebSession.SNAPSHOT, shadowOf(browser).lastEvaluatedJavascript)
        val json = mapper.writeValueAsString(mapOf("url" to url, "html" to html, "readyState" to ready,
            "documentId" to id, "hasShowPlayer" to true,
            "frames" to frames.map { mapOf("url" to it.url, "html" to it.html) }))
        shadowOf(browser).lastEvaluatedJavascriptCallback.onReceiveValue(mapper.writeValueAsString(json))
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

    @Test fun wrapperHttpDenialStopsTimersPersistsPauseAndNeverEnablesAnAutomaticRetry() {
        val (dialog, browser) = open()
        browser.webViewClient.onReceivedHttpError(browser, request("https://ekino.ws/watch/f/player/id"),
            WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "denied".byteInputStream()))
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
            reply(web, material, index.toDouble())
            assertEquals(material, page.await().url)
            val player = Player("player", "id$index", "Lektor PL")
            val frame = async(dispatcher) { session.player(material, player) }
            reply(web, material, index.toDouble())
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

    @Test fun searchAfterPlaybackBootstrapsARealPageAndNeverReturnsThePreviousSearchDocument() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = async(dispatcher) { session.page("https://ekino.ws/movie/show/first/1") }
        val web = browser(); reply(web, "https://ekino.ws/movie/show/first/1", 1.0); first.await()
        session.finishPlayback()
        val search = async(dispatcher) { session.search("Zabójcza") }
        assertEquals("${Site.ENTRY}/", shadowOf(web).lastLoadedUrl)
        reply(web, "https://ekino.ws/", 2.0)
        assertTrue(shadowOf(web).lastEvaluatedJavascript.contains("HTMLFormElement.prototype.submit"))
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue("true")
        reply(web, "https://ekino.ws/", 2.0)
        assertFalse(search.isCompleted)
        advanceTimeBy(200); runCurrent()
        val url = "https://ekino.ws/search/qf/proba"
        reply(web, url, 3.0)
        assertEquals(url, search.await().url)

        val second = async(dispatcher) { session.search("Powrót") }
        reply(web, url, 3.0) // Inspect the existing page before submitting its form.
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue("true")
        reply(web, url, 3.0) // Same search path, but this is still the old document.
        assertFalse(second.isCompleted)
        advanceTimeBy(200); runCurrent()
        reply(web, url, 4.0)
        assertEquals(url, second.await().url)
        assertNull(state.rejection())
    }
}
