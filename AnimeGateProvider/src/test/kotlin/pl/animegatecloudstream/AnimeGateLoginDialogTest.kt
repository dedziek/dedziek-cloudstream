package pl.animegatecloudstream

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE)
class AnimeGateLoginDialogTest {
    @Test fun portraitAllocatesVisiblePageAndActions() = layout(393, 800)
    @Test fun reducedHeightKeepsPageAndCloseVisible() = layout(800, 393)
    private fun layout(w: Int, h: Int) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get().apply { setTheme(android.R.style.Theme_Material_Light_NoActionBar) }
        controller.setup()
        val page = View(activity)
        val dialog = AnimeGateLoginDialog(activity, page, TextView(activity).apply { text = "Stan logowania" })
        try {
            dialog.show()
            val density = activity.resources.displayMetrics.density
            val width = (w * density).toInt(); val height = (h * density).toInt()
            val root = dialog.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            assertTrue(page.height >= 80 * density)
            assertTrue((dialog.saveButton.parent as ViewGroup).top >= page.bottom)
            assertTrue((dialog.closeButton.parent as ViewGroup).bottom <= height)
            assertTrue(dialog.closeButton.isShown)
            assertFalse(dialog.catalogButton.isShown)
            assertFalse(dialog.reportButton.isShown)
            assertTrue(dialog.moreButton.isShown)
            dialog.moreButton.performClick()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            assertTrue(dialog.catalogButton.isShown)
            assertTrue(page.height >= 80 * density)
            assertTrue(dialog.catalogButton.bottom + (dialog.catalogButton.parent as ViewGroup).top + (dialog.catalogButton.parent.parent as ViewGroup).top <= height)
            dialog.moreButton.performClick()
            assertFalse(dialog.catalogButton.isShown)
            dialog.closeButton.performClick(); assertFalse(dialog.isShowing)
        } finally { dialog.dismiss(); controller.pause().stop().destroy() }
    }
}
