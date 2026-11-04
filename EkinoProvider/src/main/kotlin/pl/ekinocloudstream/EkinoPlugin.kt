package pl.ekinocloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class EkinoPlugin : Plugin() {
    override fun load(context: Context) {
        val session = EkinoSession(context.applicationContext)
        registerMainAPI(EkinoProvider(session))
        openSettings = { EkinoLoginUi.show(it, session) }
    }
}
