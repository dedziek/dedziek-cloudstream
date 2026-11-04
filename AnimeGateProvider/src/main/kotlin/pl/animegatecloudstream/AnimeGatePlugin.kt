package pl.animegatecloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeGatePlugin : Plugin() {
    override fun load(context: Context) {
        val session = AnimeGateSession(context.applicationContext)
        registerMainAPI(AnimeGateProvider(session))
        openSettings = { settingsContext -> AnimeGateLoginUi.show(settingsContext, session) }
    }
}
