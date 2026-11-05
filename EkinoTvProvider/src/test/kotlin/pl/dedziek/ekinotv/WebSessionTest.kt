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

    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        activity = controller.setup().get()
        activity.getSharedPreferences("dedziek_ekino_tv_v1", Context.MODE_PRIVATE).edit().clear().commit()
        state = SessionState(activity)
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
}
