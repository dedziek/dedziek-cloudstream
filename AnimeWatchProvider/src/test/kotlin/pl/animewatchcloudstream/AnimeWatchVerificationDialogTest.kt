package pl.animewatchcloudstream

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE)
class AnimeWatchVerificationDialogTest {
    @Test fun portraitKeepsThePageVisibleAndCancelBelowIt() {
        checkLayout(393, 800, android.R.style.Theme_Material_Light_NoActionBar)
    }

    @Test fun landscapeAndReducedHeightKeepThePageAndCancelVisible() {
        checkLayout(800, 393, android.R.style.Theme_Material_NoActionBar)
        checkLayout(393, 350, android.R.style.Theme_Material_NoActionBar)
    }

    private fun checkLayout(widthDp: Int, heightDp: Int, theme: Int) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(theme)
        controller.setup()
        // Exercise real layout allocation; Robolectric's WebView provider does not measure.
        val browser = View(activity)
        val dialog = AnimeWatchVerificationDialog(activity, browser)
        var cancelled = false
        dialog.setOnCancelListener { cancelled = true }
        try {
            dialog.show()
            val density = activity.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            val root = dialog.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            assertTrue("Verification page must remain visible", browser.height >= 100 * density)
            assertTrue(browser.width >= width * 0.9)
            assertTrue(dialog.cancelButton.isShown && dialog.cancelButton.height > 0)
            assertTrue(dialog.cancelButton.top >= browser.bottom)
            assertTrue(dialog.cancelButton.bottom <= height)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, dialog.window!!.attributes.height)
            dialog.cancelButton.performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(cancelled)
            assertFalse(dialog.isShowing)
        } finally {
            dialog.dismiss()
            (browser.parent as? ViewGroup)?.removeView(browser)
            controller.pause().stop().destroy()
        }
    }
}
