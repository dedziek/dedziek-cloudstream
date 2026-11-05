package pl.ekinocloudstream

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Own the entire dialog content so a themed AlertDialog panel cannot collapse the browser. */
internal class EkinoLoginDialog(activity: Activity, browser: View, status: TextView) : Dialog(activity) {
    val saveButton = Button(context).apply { text = "Zapisz sesję" }
    val refreshButton = Button(context).apply { text = "Odśwież" }
    val closeButton = Button(context).apply { text = "Zamknij" }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "Logowanie — ekino.ws"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(padding, padding, padding, padding / 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = "Zaloguj się w formularzu strony i wybierz „Zapisz sesję”."
                textSize = 14f
                setPadding(padding, 0, padding, padding / 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            status.maxLines = 3
            status.ellipsize = TextUtils.TruncateAt.END
            status.textSize = 14f
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(padding / 2, 0, padding / 2, padding / 2)
                for (button in listOf(refreshButton, closeButton, saveButton)) {
                    button.textSize = 14f
                    button.isAllCaps = false
                    button.minWidth = 0
                    button.minimumWidth = 0
                    button.setPadding(padding / 2, 0, padding / 2, 0)
                    addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // A definite root height is essential: the browser receives the remaining height.
        setContentView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        closeButton.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
