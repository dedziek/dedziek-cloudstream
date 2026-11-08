package pl.ogladajanimecloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OgladajAnimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(OgladajAnimeProvider())
    }
}
