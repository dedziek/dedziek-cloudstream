package pl.filmancloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmanPlugin : Plugin() {
    override fun load(context: Context) {
        val session = FilmanSession(context.applicationContext)
        registerMainAPI(FilmanProvider(session))
        openSettings = { settingsContext -> FilmanLoginUi.show(settingsContext, session) }
    }
}
