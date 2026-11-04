package pl.animegatecloudstream

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

internal class AnimeGateLoginDialog(activity: Activity, browser: View, status: TextView) : Dialog(activity) {
    val saveButton = Button(context).apply { text = "Zapisz i sprawdź" }
    val checkButton = Button(context).apply { text = "Sprawdź zapis" }
    val reportButton = Button(context).apply { text = "Raport" }
    val clearButton = Button(context).apply { text = "Usuń sesję" }
    val closeButton = Button(context).apply { text = "Zamknij" }
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "AnimeGate — test logowania"; textSize = 20f; setTypeface(typeface, Typeface.BOLD)
                setPadding(pad, pad, pad, pad / 2)
            }, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(context).apply {
                text = "Zaloguj się przez Discord. Po powrocie do AnimeGate wybierz „Zapisz i sprawdź”. To prototyp bez katalogu i odtwarzania."
                textSize = 14f; setPadding(pad, 0, pad, pad / 2)
            }, LinearLayout.LayoutParams(-1, -2))
            status.maxLines = 3; status.ellipsize = TextUtils.TruncateAt.END; status.textSize = 14f
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
            for (buttons in listOf(listOf(saveButton, checkButton), listOf(reportButton, clearButton, closeButton))) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (button in buttons) {
                        button.textSize = 13f; button.isAllCaps = false; button.minWidth = 0; button.minimumWidth = 0
                        button.setPadding(pad / 2, 0, pad / 2, 0)
                        addView(button, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
        window?.apply {
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        closeButton.setOnClickListener { dismiss() }
    }
    override fun onStart() { super.onStart(); window?.setLayout(-1, -1) }
}
