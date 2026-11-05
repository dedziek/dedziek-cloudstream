package pl.ekinocloudstream

import android.app.Activity
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EkinoVerificationDialogTest {
    private val material = "${EkinoParser.BASE}/movie/show/klub-winlandii/31026"

    @Test fun windowOpensTheSelectedMaterialAndCancelDoesNotSaveOrResume() = withWindow { dialog, web, saved, closed ->
        assertEquals(material, web.url)
        assertEquals("Gotowe — kontynuuj", dialog.saveButton.text.toString())
        dialog.closeButton.performClick()
        idle()
        assertEquals(0, saved.size)
        assertEquals(listOf(false), closed)
    }

    @Test fun userCompletionSavesOnlyEkinoCookiesAndActualWebViewAgentThenResumes() = withWindow { dialog, web, saved, closed ->
        val manager = CookieManager.getInstance()
        manager.setCookie(EkinoParser.BASE, "PHPSESSID=EKINO_FIXTURE; Path=/; Secure")
        manager.setCookie("https://foreign.example/", "foreign=FOREIGN_FIXTURE; Path=/; Secure")
        val agent = web.settings.userAgentString
        dialog.saveButton.performClick()
        idle()
        assertFalse(dialog.isShowing)
        assertEquals(listOf(true), closed)
        assertEquals(1, saved.size)
        assertTrue(saved[0].first.contains("EKINO_FIXTURE"))
        assertFalse(saved[0].first.contains("FOREIGN_FIXTURE"))
        assertEquals(agent, saved[0].second)
    }

    @Test fun httpDenialCannotBeMarkedAsCompleted() = withWindow { dialog, web, saved, closed ->
        CookieManager.getInstance().setCookie(EkinoParser.BASE, "PHPSESSID=FIXTURE; Path=/; Secure")
        web.webViewClient.onReceivedHttpError(web, request(material),
            WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), null))
        dialog.saveButton.performClick()
        idle()
        assertTrue(dialog.isShowing)
        assertTrue(saved.isEmpty())
        assertTrue(closed.isEmpty())
    }

    @Test fun foreignTopLevelPostIsBlockedDuringVerification() = withWindow { _, web, saved, _ ->
        val response = web.webViewClient.shouldInterceptRequest(web, request("https://foreign.example/submit"))
        assertEquals(403, response?.statusCode)
        assertTrue(saved.isEmpty())
    }

    private fun withWindow(check: (EkinoLoginDialog, WebView, MutableList<Pair<String, String>>, MutableList<Boolean>) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        controller.setup()
        val saved = mutableListOf<Pair<String, String>>()
        val closed = mutableListOf<Boolean>()
        val dialog = requireNotNull(EkinoLoginUi.openLogin(activity, EkinoSession(activity), material, verification = true,
            saveSession = { cookie, agent -> saved.add(cookie to agent) }, onClosed = { closed.add(it) }))
        idle()
        fun web(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { web(view.getChildAt(it)) } else null
        val browser = requireNotNull(web(dialog.findViewById(android.R.id.content)))
        try { check(dialog, browser, saved, closed) }
        finally { dialog.dismiss(); idle(); controller.pause().stop().destroy() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "POST"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}
