package pl.animegatecloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeGatePlugin : Plugin() {
    override fun load(context: Context) {
        val session = AnimeGateSession(context.applicationContext)
        // Auth feasibility is the first gate. Do not register a fictitious catalogue API.
        openSettings = { settingsContext -> AnimeGateLoginUi.show(settingsContext, session) }
    }
}
