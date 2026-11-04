package pl.animewatchcloudstream

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-height content keeps the real verification page visible across dialog themes. */
internal class AnimeWatchVerificationDialog(activity: Activity, browser: View) : Dialog(activity) {
    val cancelButton = Button(context).apply { text = "Anuluj" }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "Byse — weryfikacja"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(padding, padding, padding, padding / 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = "Naciśnij odtwarzanie i wykonaj wymaganą weryfikację. Okno zamknie się po uzyskaniu wideo."
                textSize = 14f
                setPadding(padding, 0, padding, padding / 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(cancelButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window?.apply {
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        cancelButton.setOnClickListener { cancel() }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
