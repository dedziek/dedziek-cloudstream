package pl.dedziek.ekinotv

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Status and actions share one scrollable layout; AlertDialog message/list modes are exclusive. */
internal object SettingsUi {
    const val VERIFY = "Otwórz weryfikację"
    const val SITE = "Otwórz stronę / logowanie"
    const val REPORT = "Kopiuj raport"

    fun show(activity: Activity, status: String, hint: String,
             onVerify: () -> Unit, onSite: () -> Unit, onReport: () -> Unit) {
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(TextView(activity).apply {
                text = status; textSize = 16f
                setPadding(0, 0, 0, padding / 2)
            })
            addView(TextView(activity).apply {
                text = hint; textSize = 14f
                setPadding(0, 0, 0, padding / 2)
            })
        }
        val scroll = ScrollView(activity).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        lateinit var menu: AlertDialog
        fun action(label: String, closeMenu: Boolean, run: () -> Unit) {
            content.addView(Button(activity).apply {
                text = label; isAllCaps = false
                setOnClickListener {
                    if (closeMenu) menu.dismiss()
                    run()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = padding / 2
            })
        }
        action(VERIFY, true, onVerify)
        action(SITE, true, onSite)
        action(REPORT, false, onReport)
        menu = AlertDialog.Builder(activity).setTitle("Ekino TV v9")
            .setView(scroll).setNegativeButton("Zamknij", null).create()
        menu.show()
    }
}
