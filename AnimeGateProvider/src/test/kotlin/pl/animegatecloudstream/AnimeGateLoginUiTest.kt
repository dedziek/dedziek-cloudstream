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
import android.webkit.CookieManager
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
    @Test fun backendDiscordAndFrontendLoginFlowDoesNotTriggerABlock() = withLogin { activity, dialog, web ->
        val client = web.webViewClient
        val backend = "https://api.animegate.pl/api/v1/auth/discord"
        val auth = "https://auth.animegate.pl/api/auth/discord/start"
        val discord = "https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fauth.animegate.pl%2Fapi%2Fauth%2Fdiscord%2Fcallback&state=SECRET"
        val callback = "https://auth.animegate.pl/api/auth/discord/callback?code=SECRET&state=PRIVATE"
        assertNull(client.shouldInterceptRequest(web, request("https://api.animegate.pl/api/v7/auth/session")))
        for (url in listOf(backend, auth, discord, callback, "$backend/callback?code=SECRET", AnimeGateAuthPolicy.ROOT)) {
            assertFalse(url.substringBefore('?'), client.shouldOverrideUrlLoading(web, request(url)))
            assertNull(client.shouldInterceptRequest(web, request(url)))
            client.onPageStarted(web, url, null)
        }
        client.onPageFinished(web, AnimeGateAuthPolicy.ROOT)
        val report = report(activity, dialog)
        assertTrue(report.contains("AnimeGate login prototype v4"))
        assertTrue(report.contains("api_seen=true"))
        assertTrue(report.contains("auth_seen=true"))
        assertTrue(report.contains("session_api_observed=true"))
        assertTrue(report.contains("observed_session_origin=API"))
        assertTrue(report.contains("discord_seen=true"))
        assertTrue(report.contains("returned_to_animegate=true"))
        assertTrue(report.contains("blocked_count=0"))
        assertTrue(report.contains("api_route=api.animegate.pl/api/{param}/auth/discord"))
        assertFalse(report.contains("SECRET")); assertFalse(report.contains("PRIVATE"))
    }
    @Test fun clearingObservedBackendCookiesPreservesDiscordCookies() = withLogin { _, dialog, web ->
        val backend = "https://api.animegate.pl/api/v7/auth/session"
        val manager = CookieManager.getInstance()
        manager.setCookie(backend, "api_only=PRIVATE_API; Path=/api/v7/auth; Secure")
        manager.setCookie("https://discord.com/", "discord_only=PRIVATE_DISCORD; Path=/; Secure")
        assertTrue(manager.getCookie(backend).orEmpty().contains("PRIVATE_API"))
        web.webViewClient.shouldInterceptRequest(web, request(backend))
        dialog.clearButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(manager.getCookie(backend).orEmpty().contains("PRIVATE_API"))
        assertTrue(manager.getCookie("https://discord.com/").orEmpty().contains("PRIVATE_DISCORD"))
    }
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
