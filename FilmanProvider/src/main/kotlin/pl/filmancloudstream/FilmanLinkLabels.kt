package pl.filmancloudstream

import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList

/** Change the visible name while retaining the extractor's playback and verification data. */
internal object FilmanLinkLabels {
    // Extractor callbacks are not suspending, so use the synchronous constructors here.
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun apply(link: ExtractorLink, player: FilmanParser.Player): ExtractorLink {
        val name = player.displayName(link.name)
        return when (link) {
            is ExtractorLinkPlayList -> link.copy(name = name)
            is DrmExtractorLink -> DrmExtractorLink(
                source = link.source, name = name, url = link.url, referer = link.referer,
                quality = link.quality, type = link.type, headers = link.headers,
                extractorData = link.extractorData, kid = link.kid, key = link.key,
                uuid = link.uuid, kty = link.kty, keyRequestParameters = link.keyRequestParameters,
                licenseUrl = link.licenseUrl
            ).apply { audioTracks = link.audioTracks }
            else -> ExtractorLink(
                source = link.source, name = name, url = link.url, referer = link.referer,
                quality = link.quality, headers = link.headers, extractorData = link.extractorData,
                type = link.type, audioTracks = link.audioTracks
            )
        }
    }
}
