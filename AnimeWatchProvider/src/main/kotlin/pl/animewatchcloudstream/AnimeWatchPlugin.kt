package pl.animewatchcloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeWatchPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeWatchProvider())
        registerExtractorAPI(AnimeWatchByse())
    }
}
