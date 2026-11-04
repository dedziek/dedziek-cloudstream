package pl.animewatchcloudstream

import java.net.URI

/** Shared by the background player and its regression tests; no Android dependencies. */
object AnimeWatchPlayback {
    data class Stream(val url: String, val headers: Map<String, String>, val referer: String, val isHls: Boolean)
    data class PlayerSource(val url: String = "", val type: String = "", val referer: String = "", val error: String? = null)

    class VerificationRequired : Exception("Byse wymaga potwierdzenia w odtwarzaczu.")

    /** Resume the same load request after the user completes a required verification. */
    suspend fun resolveWithVerification(resolve: suspend (interactive: Boolean) -> Stream?): Stream? =
        try { resolve(false) } catch (_: VerificationRequired) { resolve(true) }

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

    fun isPlayerPage(url: String, pages: List<URI>): Boolean {
        val source = publicHttps(url) ?: return false
        return pages.any { sameOrigin(it, source) && it.path.trimEnd('/') == source.path.trimEnd('/') }
    }

    fun isPlayerScript(url: String, referer: String?, pages: List<URI>): Boolean {
        val script = publicHttps(url) ?: return false
        val page = referer ?: return false
        val source = publicHttps(page) ?: return false
        return script.path.matches(Regex("/assets/index-[A-Za-z0-9_-]+\\.js")) &&
            sameOrigin(script, source) && pages.any { sameOrigin(it, source) } &&
            (source.path.isNullOrEmpty() || source.path == "/" || isPlayerPage(page, pages))
    }

    fun acceptsMessage(nonce: String, expectedNonce: String, source: PlayerSource, pages: List<URI>): Boolean =
        nonce == expectedNonce && isPlayerPage(source.referer, pages)

    /** Metadata is observed in the actual /e/ parent before it creates its player iframe. */
    fun discoveredFrame(nonce: String, expectedNonce: String, source: PlayerSource, parent: URI): URI? {
        if (source.type != "embed_frame" || source.error != null ||
            !acceptsMessage(nonce, expectedNonce, source, listOf(parent))) return null
        val code = Regex("^/e/([A-Za-z0-9]+)/?$").matchEntire(parent.path)?.groupValues?.get(1) ?: return null
        val frame = publicHttps(source.url) ?: return null
        if (!frame.path.matches(Regex("/[A-Za-z0-9_-]{1,32}/${Regex.escape(code)}/?"))) return null
        if (isPlayerPage(source.url, listOf(parent))) return null
        return frame
    }

    fun acceptsSource(nonce: String, expectedNonce: String, source: PlayerSource, pages: List<URI>): Boolean =
        acceptsMessage(nonce, expectedNonce, source, pages) && source.error == null && publicHttps(source.url) != null &&
            (isMedia(source.url) || source.type.lowercase() in setOf("hls", "m3u8", "mp4", "video/mp4", "application/x-mpegurl", "application/vnd.apple.mpegurl"))

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

    // Appended only to the hosting's entry script, so it runs inside the actual
    // cross-origin player frame. evaluateJavascript() on the outer page cannot do that.
    val frameScriptTemplate = """
        ;(function () {
            // Observe the parent's normal metadata response with its real WebView
            // cookies/UA. Do not issue another request or alter the response/body.
            // Register the frame before React can create it and load its entry script.
            var originalFetch = window.fetch;
            if (typeof originalFetch === 'function') {
                window.fetch = function (input, options) {
                    var observe = false;
                    var code = '';
                    try {
                        var page = new URL(location.href);
                        var match = page.pathname.match(/^\/e\/([A-Za-z0-9]+)\/?$/);
                        var target = new URL(input && typeof input.url === 'string' ? input.url : String(input), page.href);
                        var method = String((options && options.method) || (input && input.method) || 'GET').toUpperCase();
                        code = match ? match[1] : '';
                        observe = !!code && method === 'GET' && target.origin === page.origin &&
                            target.pathname === '/api/videos/' + code + '/embed/details';
                    } catch (_) {}
                    var pending = originalFetch.apply(this, arguments);
                    if (!observe) return pending;
                    return pending.then(function (response) {
                        if (!response.ok || typeof response.clone !== 'function') return response;
                        try {
                            return response.clone().json().then(function (details) {
                                try {
                                    if (details && details.code === code && typeof details.embed_frame_url === 'string' &&
                                        window.AnimeWatchBridge && typeof window.AnimeWatchBridge.postSource === 'function') {
                                        window.AnimeWatchBridge.postSource('__AW_NONCE__', JSON.stringify({
                                            type: 'embed_frame', url: details.embed_frame_url, referer: location.href
                                        }));
                                    }
                                } catch (_) {}
                                return response;
                            }, function () { return response; });
                        } catch (_) { return response; }
                    });
                };
            }
            var stopped = false;
            var attempts = 0;
            var gateReported = false;
            function poll() {
                if (stopped || ++attempts > 450) return;
                try {
                    var result = __AW_READ_SOURCE__;
                    if (result && window.AnimeWatchBridge && typeof window.AnimeWatchBridge.postSource === 'function') {
                        window.AnimeWatchBridge.postSource('__AW_NONCE__', result);
                        stopped = true;
                        return;
                    }
                    var gate = document.querySelector('.captcha-gate');
                    if (gate && !gateReported && window.AnimeWatchBridge) {
                        window.AnimeWatchBridge.postSource('__AW_NONCE__', JSON.stringify({ error: 'captcha_required', referer: location.href }));
                        gateReported = true;
                    }
                } catch (_) { /* The page may still be initialising its player. */ }
                setTimeout(poll, 400);
            }
            window.addEventListener('pagehide', function () { stopped = true; });
            setTimeout(poll, 0);
        })();
    """.trimIndent()

    fun frameScript(nonce: String): String {
        require(nonce.matches(Regex("[a-f0-9-]{36}")))
        return frameScriptTemplate.replace("__AW_READ_SOURCE__", playerScript.removeSuffix(";"))
            .replace("__AW_NONCE__", nonce)
    }
}
