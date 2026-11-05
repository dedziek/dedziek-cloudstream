package pl.ekinocloudstream

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE)
class EkinoManualVerificationTest {
    private val movie = "https://ekino.ws/movie/show/rescued-hd-2026-napisy/43324"

    @Test fun rememberedFailureOpensNoBrowserUntilTheUserChoosesVerification() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            val session = EkinoSession(activity)
            session.rememberVerificationPage("$movie?token=PRIVATE_FIXTURE#player")
            assertEquals(movie, session.verificationPage())
            EkinoLoginUi.show(activity, session)
            val settings = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(settings.isShowing)
            assertNull(findBrowser(settings.window!!.decorView))
            shadowOf(settings).clickOnItem(1)
            // Dialog dispatches OnShowListener through the main Handler.
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog is EkinoLoginDialog)
            val web = requireNotNull(findBrowser(dialog.window!!.decorView))
            assertEquals(movie, shadowOf(web).lastLoadedUrl)
            assertTrue(web.settings.javaScriptEnabled)
            assertTrue(web.settings.domStorageEnabled)
            assertTrue(web.settings.mediaPlaybackRequiresUserGesture)
            (dialog as EkinoLoginDialog).closeButton.performClick()
            assertFalse(dialog.isShowing)
            assertFalse(session.hasSession()) // Opening or closing is not a successful verification/save.
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun missingOrInvalidTargetDoesNotOpenALoginPageInstead() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            val session = EkinoSession(activity)
            session.rememberVerificationPage("https://foreign.example/e/PRIVATE_FIXTURE")
            assertNull(session.verificationPage())
            EkinoLoginUi.show(activity, session)
            val settings = ShadowAlertDialog.getLatestAlertDialog()
            shadowOf(settings).clickOnItem(1)
            assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("Najpierw uruchom materiał"))
            assertNull(findBrowser(ShadowDialog.getLatestDialog().window!!.decorView))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun pauseSurvivesSessionRecreationAndOpeningSettingsDoesNotResumeIt() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = EkinoSession(activity)
        try {
            session.requestGate.pause()
            val reopened = EkinoSession(activity)
            assertTrue(reopened.requestGate.isPaused)
            reopened.clear()
            assertTrue(reopened.requestGate.isPaused)
            EkinoLoginUi.show(activity, reopened)
            val settings = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(reopened.requestGate.isPaused)
            shadowOf(settings).clickOnItem(2)
            assertFalse(reopened.requestGate.isPaused)
            assertFalse(EkinoSession(activity).requestGate.isPaused)
        } finally {
            session.requestGate.resume()
            controller.pause().stop().destroy()
        }
    }

    private fun findBrowser(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findBrowser(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
