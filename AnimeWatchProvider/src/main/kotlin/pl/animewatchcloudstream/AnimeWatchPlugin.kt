package pl.animewatchcloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeWatchPlugin : Plugin() {
    override fun load(context: Context) {
        val verification = AnimeWatchPlaybackSession()
        registerMainAPI(AnimeWatchProvider(verification))
        registerExtractorAPI(AnimeWatchByse())
        openSettings = { settingsContext -> AnimeWatchVerificationUi.show(settingsContext, verification) }
    }
}
