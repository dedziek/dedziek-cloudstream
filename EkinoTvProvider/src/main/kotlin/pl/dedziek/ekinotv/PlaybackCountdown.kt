package pl.dedziek.ekinotv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** A read-only notice above CloudStream's loader; its Cancel/Back controls keep receiving input. */
internal class PlaybackCountdown(private val activitySource: () -> Activity?) {
    private var window: Dialog? = null
    private var message: TextView? = null
    private var remaining: Int? = null

    fun update(seconds: Int?) {
        if (seconds == null || seconds !in 1..120) { dismiss(); return }
        val activity = activitySource()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: run { dismiss(); return }
        if (remaining == seconds && window?.isShowing == true) return
        if (window == null) {
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply { setColor(Color.rgb(32, 35, 40)); cornerRadius = dp(12).toFloat() }
                addView(TextView(activity).apply {
                    text = "Ekino TV"; textSize = 14f; setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                })
                message = TextView(activity).apply {
                    textSize = 16f; setTextColor(Color.WHITE); setPadding(0, dp(4), 0, 0)
                    accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
                }
                addView(message)
            }
            val notice = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(content)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    attributes = attributes.apply { y = dp(24); windowAnimations = 0 }
                }
            }
            // A cosmetic notice must not turn a closing Activity into a playback failure.
            try {
                notice.show()
                notice.window?.setLayout(minOf(activity.resources.displayMetrics.widthPixels - dp(32), dp(480)), -2)
                window = notice
            } catch (_: WindowManager.BadTokenException) { message = null; return }
        }
        remaining = seconds
        message?.text = "Oczekiwanie na odtwarzacz: $seconds s"
    }

    fun dismiss() {
        window?.dismiss()
        window = null; message = null; remaining = null
    }
}
