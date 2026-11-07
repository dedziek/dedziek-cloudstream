package pl.dedziek.ekinotv

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28, 35])
class PlaybackCountdownTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun countdownRemainsVisibleAboveLoadingAndDoesNotTakeInputFromCloudStream() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val notice = PlaybackCountdown { controller.get() }
        try {
            notice.update(9)
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val flags = dialog.window!!.attributes.flags
            assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            val texts = views(dialog.window!!.decorView).filterIsInstance<TextView>()
            assertTrue(texts.map { "${it.text}: attached=${it.isAttachedToWindow}, shown=${it.isShown}" }.toString(),
                texts.any { it.isAttachedToWindow && it.isShown && it.text.contains("9 s") })
            notice.update(7)
            assertSame(dialog, ShadowDialog.getLatestDialog())
            assertTrue(texts.any { it.text.contains("7 s") })
            notice.update(null)
            assertFalse(dialog.isShowing)
        } finally { notice.dismiss(); controller.pause().stop().destroy() }
    }
}
