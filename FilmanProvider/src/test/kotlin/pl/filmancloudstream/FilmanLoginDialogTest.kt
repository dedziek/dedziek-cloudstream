package pl.filmancloudstream

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE)
class FilmanLoginDialogTest {
    @Test fun portraitKeepsBrowserVisibleAndButtonsBelowIt() {
        checkLayout(393, 800, android.R.style.Theme_Material_Light_NoActionBar, "Ładowanie strony logowania…")
    }

    @Test fun landscapeKeepsBrowserVisibleAndButtonsBelowIt() {
        checkLayout(800, 393, android.R.style.Theme_Material_NoActionBar, "Wykonaj weryfikację na stronie.")
    }

    @Test fun reducedHeightAndLongErrorKeepBrowserAndActionsVisible() {
        checkLayout(393, 350, android.R.style.Theme_Material_NoActionBar,
            "Strona odmówiła dostępu (HTTP 403). Jeśli Cloudflare wyświetla „Sorry, you have been blocked”, sprawdź ten sam adres w zwykłej przeglądarce. Zapis sesji nie usuwa blokady serwisu.")
    }

    private fun checkLayout(widthDp: Int, heightDp: Int, theme: Int, message: String) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(theme)
        controller.setup()
        val web = WebView(activity)
        val status = TextView(activity).apply { text = message }
        val dialog = FilmanLoginDialog(activity, web, status)
        try {
            dialog.show()
            val density = activity.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            val root = dialog.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)

            // A loaded page cannot be seen when the browser collapses to zero, as in v3.
            assertTrue("Browser must have room to display the login form", web.height >= 100 * density)
            assertTrue("Browser must span the window", web.width >= width * 0.9)
            val actions = dialog.saveButton.parent as ViewGroup
            assertTrue("Actions must remain below the browser", actions.top >= web.bottom)
            assertTrue("Actions must remain inside the window", actions.bottom <= height)
            for (button in listOf(dialog.refreshButton, dialog.closeButton, dialog.saveButton)) {
                assertTrue("Each action must be visible", button.isShown && button.width > 0 && button.height > 0)
            }
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, dialog.window!!.attributes.height)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, dialog.window!!.attributes.softInputMode)
            dialog.closeButton.performClick()
            assertFalse(dialog.isShowing)
        } finally {
            dialog.dismiss()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
            controller.pause().stop().destroy()
        }
    }
}
