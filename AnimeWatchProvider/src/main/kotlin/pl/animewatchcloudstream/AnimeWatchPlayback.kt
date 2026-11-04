package pl.animewatchcloudstream

import java.net.URI

/** Shared by the background player and its regression tests; no Android dependencies. */
object AnimeWatchPlayback {
    private val publicHost = Regex("""(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}$""")
    private val localSuffixes = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".test", ".invalid")
    private val mediaPath = Regex("""(?i)\.(?:m3u8|mp4)$""")

    fun publicHttps(url: String): URI? = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            host.length > 253 || !publicHost.matches(host) || localSuffixes.any(host::endsWith)) return null
        uri
    }.getOrNull()

    fun sameOrigin(first: URI, second: URI): Boolean = first.host.equals(second.host, ignoreCase = true)

    fun isMedia(url: String): Boolean = publicHttps(url)?.path?.let(mediaPath::containsMatchIn) == true

    fun isHls(url: String, type: String? = null): Boolean =
        publicHttps(url)?.path?.endsWith(".m3u8", true) == true ||
            type?.lowercase() in setOf("hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl")

    /** Read the content playlist before playback; no tap or CAPTCHA interaction is needed. */
    val playerScript = """
        (function () {
            function media(value, type) {
                if (typeof value !== 'string' || !value || value.indexOf('blob:') === 0) return null;
                var url;
                try { url = new URL(value, location.href); } catch (_) { return null; }
                if (url.protocol !== 'https:') return null;
                var kind = String(type || '').toLowerCase();
                if (!/\.(m3u8|mp4)$/i.test(url.pathname) &&
                    ['hls', 'm3u8', 'mp4', 'video/mp4', 'application/x-mpegurl', 'application/vnd.apple.mpegurl'].indexOf(kind) < 0) return null;
                return { url: url.href, type: kind, referer: location.href };
            }
            try {
                if (typeof window.jwplayer === 'function') {
                    var player = window.jwplayer();
                    var item = typeof player.getPlaylistItem === 'function' ? player.getPlaylistItem() : null;
                    if (!item && typeof player.getPlaylist === 'function') item = (player.getPlaylist() || [])[0];
                    if (item) {
                        var sources = item.sources || [];
                        for (var i = 0; i < sources.length; i++) {
                            var source = media(sources[i].file || sources[i].url, sources[i].type || item.type);
                            if (source) return JSON.stringify(source);
                        }
                        var file = media(item.file, item.type);
                        if (file) return JSON.stringify(file);
                    }
                }
            } catch (_) { /* The player may still be initialising. Poll again on the next tick. */ }
            var video = document.querySelector('.jwplayer:not(.jw-flag-ads) video.jw-video');
            if (video) {
                var current = media(video.currentSrc || video.src, video.type);
                if (current) return JSON.stringify(current);
            }
            return null;
        })();
    """.trimIndent()
}
