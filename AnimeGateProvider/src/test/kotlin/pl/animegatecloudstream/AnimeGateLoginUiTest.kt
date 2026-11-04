package pl.animegatecloudstream

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnimeGateLoginUiTest {
    @Test fun blankNavigationContinuesAndBlockedNavigationCanBeDiagnosedFromTheReport() = withLogin { activity, dialog, web ->
        val client = web.webViewClient
        assertFalse(client.shouldOverrideUrlLoading(web, request("about:blank")))
        assertTrue(client.shouldOverrideUrlLoading(web, request("https://auth.example.com/login?state=SECRET")))
        val report = report(activity, dialog)
        assertTrue(report.contains("browser_state=REDIRECT_BLOCKED"))
        assertTrue(report.contains("first_block_source=MAIN_NAVIGATION"))
        assertTrue(report.contains("first_block_host=auth.example.com"))
        assertFalse(report.contains("SECRET"))
    }

    @Test fun aPostNavigationBlockedOnTheRequestThreadAlsoAppearsInTheReport() = withLogin { activity, dialog, web ->
        val response = AtomicReference<WebResourceResponse?>()
        val client = web.webViewClient
        val worker = Thread { response.set(client.shouldInterceptRequest(web, request("https://auth.example.com/callback?code=SECRET", "POST"))) }
        worker.start(); worker.join(5000)
        assertFalse(worker.isAlive)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(403, response.get()!!.statusCode)
        val report = report(activity, dialog)
        assertTrue(report.contains("browser_state=REDIRECT_BLOCKED"))
        assertTrue(report.contains("first_block_source=MAIN_REQUEST"))
        assertTrue(report.contains("first_block_host=auth.example.com"))
        assertFalse(report.contains("SECRET"))
    }

    private fun request(url: String, method: String = "GET") = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = method
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
    private fun report(activity: Activity, dialog: AnimeGateLoginDialog): String {
        dialog.reportButton.performClick()
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip!!.getItemAt(0).text.toString()
    }
    private fun withLogin(test: (Activity, AnimeGateLoginDialog, WebView) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get().apply { setTheme(android.R.style.Theme_Material_Light_NoActionBar) }
        controller.setup()
        AnimeGateLoginUi.show(activity, AnimeGateSession(activity))
        val dialog = ShadowDialog.getLatestDialog() as AnimeGateLoginDialog
        fun browser(view: View): WebView? = if (view is WebView) view else (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { browser(group.getChildAt(it)) }
        }
        try { test(activity, dialog, browser(dialog.findViewById(android.R.id.content))!!) }
        finally { dialog.dismiss(); controller.pause().stop().destroy() }
    }
}
