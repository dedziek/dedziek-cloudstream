package pl.animegatecloudstream

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnimeGateMediaProbeTest {
    private val media = "https://cdn.example.org/opaque-stream?token=synthetic"
    private val iframe = "https://playmogo.com/e/fixture"
    private val hls = "#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nsegment.ts\n#EXT-X-ENDLIST\n"
    private fun response(body: String, mime: String = "application/octet-stream", status: Int = 206) =
        AnimeGateMediaProbe.Response(status, mime, body.toByteArray())

    @Test fun extensionlessPlaylistUsesHlsEvenWhenTheServerLabelsItAsMp4() = runBlocking {
        val probe = AnimeGateMediaProbe({ _, _ -> response(hls, "video/mp4") })
        assertEquals(AnimeGateMediaProbe.Format.HLS, probe.inspect(media, iframe, emptyMap()))
        assertEquals(AnimeGateMediaProbe.Format.HLS, AnimeGateMediaProbe.format(response("\uFEFF \n$hls")))
    }

    @Test fun dashAndMp4UseTheirActualContainerInsteadOfAnExtension() {
        assertEquals(AnimeGateMediaProbe.Format.DASH, AnimeGateMediaProbe.format(response("<?xml version='1.0'?>\n<MPD xmlns='urn:mpeg:dash:schema:mpd:2011'></MPD>")))
        val mp4 = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray()
        assertEquals(AnimeGateMediaProbe.Format.VIDEO, AnimeGateMediaProbe.format(AnimeGateMediaProbe.Response(206, "application/octet-stream", mp4)))
    }

    @Test fun htmlJsonEmptyAndUnknownBodiesAreNotReportedAsVideo() {
        for (body in listOf("<!doctype html><html>Denied</html>", "<html>Login</html>", "{\"error\":\"expired\"}", "", "Access denied")) {
            val error = assertThrows(AnimeGateMediaException::class.java) { AnimeGateMediaProbe.format(response(body, "video/mp4")) }
            assertFalse(error.message!!.contains("token"))
        }
    }

    @Test fun probePreservesHostingHeadersAndDoesNotReturnItsRangeHeaderToThePlayer() = runBlocking {
        val original = mapOf("User-Agent" to "hosting-ua", "Origin" to "https://playmogo.com")
        var seen = emptyMap<String, String>()
        val probe = AnimeGateMediaProbe({ _, headers -> seen = headers; response(hls) }, "default-ua")
        probe.inspect(media, iframe, original)
        assertEquals("bytes=0-8191", seen["Range"])
        assertEquals("hosting-ua", seen["User-Agent"])
        assertEquals(iframe, seen["Referer"])
        assertFalse(original.containsKey("Range"))
        assertFalse(seen.containsKey("Cookie"))
    }

    @Test fun aCdnsRedirectDoesNotForwardAnotherHostsCredentials() = runBlocking {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val probe = AnimeGateMediaProbe({ url, headers ->
            calls.add(url to headers.toMap())
            if (calls.size == 1) AnimeGateMediaProbe.Response(302, "", ByteArray(0), "https://other.example.net/stream") else response(hls)
        })
        probe.inspect(media, iframe, mapOf("Cookie" to "hosting-only=synthetic", "Authorization" to "synthetic"))
        assertEquals(2, calls.size)
        assertTrue(calls[0].second.containsKey("Cookie"))
        assertFalse(calls[1].second.keys.any { it.equals("Cookie", true) || it.equals("Authorization", true) })
    }

    @Test fun missingRangeSupportAllowsOneBoundedGetFallbackButDenialsAreNotRetried() = runBlocking {
        val requests = mutableListOf<Map<String, String>>()
        val probe = AnimeGateMediaProbe({ _, headers ->
            requests.add(headers.toMap()); if (requests.size == 1) response("", status = 416) else response(hls)
        })
        assertEquals(AnimeGateMediaProbe.Format.HLS, probe.inspect(media, iframe, emptyMap()))
        assertTrue(requests[0].containsKey("Range")); assertFalse(requests[1].containsKey("Range"))
        for (status in listOf(401, 403, 404, 429)) {
            var count = 0
            val denied = AnimeGateMediaProbe({ _, _ -> count++; response("", status = status) })
            try { denied.inspect(media, iframe, emptyMap()); fail("HTTP $status accepted") }
            catch (error: AnimeGateMediaException) { assertTrue(error.message!!.contains("HTTP $status")) }
            assertEquals(1, count)
        }
    }

    @Test fun redirectsCannotSendAProbeToTheAnimeGateApiOrAPrivateAddress() = runBlocking {
        for (target in listOf("https://api.animegate.pl/api/v1/auth/me", "https://cdn.animegate.pl/x", "https://127.0.0.1/x", "https://host.local/x", "http://cdn.example.org/x")) {
            var count = 0
            val probe = AnimeGateMediaProbe({ _, _ -> count++; AnimeGateMediaProbe.Response(302, "", ByteArray(0), target) })
            try { probe.inspect(media, iframe, emptyMap()); fail(target) } catch (_: AnimeGateMediaException) { }
            assertEquals(1, count)
        }
    }
}
