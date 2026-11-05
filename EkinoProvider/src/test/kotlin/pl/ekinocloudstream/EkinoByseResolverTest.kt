package pl.ekinocloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EkinoByseResolverTest {
    private val embed = "https://byse.example/e/video123?token=PRIVATE_URL"
    private val mapper = ObjectMapper()
    private val challenge = """{"pow_nonce":"validation-fixture","pow_difficulty":8,"pow_token":"PRIVATE_CHALLENGE"}"""

    private fun encrypted(sources: String = """[{"url":"https://cdn.example/master.m3u8?token=PRIVATE_MEDIA","mime_type":"application/vnd.apple.mpegurl"}]""",
                          versioned: Boolean = true): JsonNode {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val parts = if (versioned) MutableList(30) { encoder.encodeToString(ByteArray(16) { 42 }) }.apply {
            this[4] = encoder.encodeToString(key.copyOfRange(0, 16))
            this[25] = encoder.encodeToString(key.copyOfRange(16, 32))
        } else listOf(encoder.encodeToString(key))
        val body = mutableMapOf<String, Any>("algorithm" to "AES-256-GCM", "iv" to encoder.encodeToString(iv),
            "payload" to encoder.encodeToString(cipher.doFinal("{\"sources\":$sources}".toByteArray())), "key_parts" to parts)
        if (versioned) body["version"] = 5
        return mapper.valueToTree(mapOf("playback" to body))
    }

    @Test fun performsThePublicPlayerSequenceOnceWithItsOwnOriginAndToken() = runBlocking {
        val requests = mutableListOf<String>()
        val resolver = EkinoByseResolver({ url, referer, body, token ->
            requests.add(url)
            assertEquals(embed, referer)
            assertTrue(url.startsWith("https://byse.example/api/videos/video123/embed/"))
            assertFalse(url.contains("PRIVATE_URL"))
            val json = mapper.readTree(body)
            when (requests.size) {
                1 -> { assertNull(token); EkinoHostResolver.Response(200, challenge) }
                2 -> {
                    assertNull(token)
                    assertEquals("PRIVATE_CHALLENGE", json.path("pow_token").asText())
                    assertEquals("169", json.path("solution").asText())
                    EkinoHostResolver.Response(200, """{"status":"ok","token":"PRIVATE_ACCESS"}""")
                }
                3 -> {
                    assertEquals("PRIVATE_ACCESS", token)
                    assertEquals("", json.path("fingerprint").path("token").asText())
                    EkinoHostResolver.Response(200, encrypted().toString())
                }
                else -> error("No retries")
            }
        })
        val result = resolver.resolve(embed)
        assertEquals(3, requests.size)
        assertEquals("Byse API OK", result.report())
        val media = result.media.single()
        assertTrue(media.hls)
        assertEquals(embed, media.referer)
        assertEquals(mapOf("Origin" to "https://byse.example"), media.headers)
        assertFalse(result.report().contains("PRIVATE"))
    }

    @Test fun denialNeverStartsWorkOrAnyFollowupRequest() = runBlocking {
        for (status in listOf(401, 403, 429)) {
            var count = 0
            val resolver = EkinoByseResolver({ _, _, _, _ -> count++; EkinoHostResolver.Response(status) },
                { _, _ -> error("Must not solve work after denial") })
            val result = resolver.resolve(embed)
            assertEquals(status, result.status)
            assertEquals(1, count)
            assertTrue(result.media.isEmpty())
        }
    }

    @Test fun aBrowserVerificationResponseIsNotTreatedAsComputationalInitialization() = runBlocking {
        var count = 0
        val resolver = EkinoByseResolver({ _, _, _, _ ->
            count++; EkinoHostResolver.Response(200, "<title>Just a moment...</title><form id=challenge-form></form>")
        }, { _, _ -> error("Must not solve Cloudflare") })
        val result = resolver.resolve(embed)
        assertEquals(EkinoHostResolver.Verification.CLOUDFLARE, result.verification)
        assertEquals(1, count)
        assertTrue(result.media.isEmpty())
    }

    @Test fun rejectsExcessiveWorkAndStopsWhenTheWorkBudgetIsExhausted() = runBlocking {
        var count = 0
        val excessive = EkinoByseResolver({ _, _, _, _ ->
            count++; EkinoHostResolver.Response(200, challenge.replace(":8", ":24"))
        }, { _, _ -> error("Unbounded work must be rejected") })
        assertEquals(EkinoByseResolver.Stage.WORK, excessive.resolve(embed).stage)
        assertEquals(1, count)
        val exhausted = EkinoByseResolver({ _, _, _, _ -> EkinoHostResolver.Response(200, challenge) }, { _, _ -> null })
        assertEquals(EkinoByseResolver.Stage.WORK, exhausted.resolve(embed).stage)
    }

    @Test fun rejectedVerificationDoesNotRequestPlaybackAndCancellationPropagates() = runBlocking {
        var count = 0
        val rejected = EkinoByseResolver({ _, _, _, _ ->
            count++; EkinoHostResolver.Response(200, if (count == 1) challenge else """{"status":"rejected"}""")
        }, { _, _ -> "169" })
        assertEquals(EkinoByseResolver.Stage.VERIFY, rejected.resolve(embed).stage)
        assertEquals(2, count)
        val cancelled = EkinoByseResolver({ _, _, _, _ -> EkinoHostResolver.Response(200, challenge) },
            { _, _ -> throw CancellationException("fixture") })
        try { cancelled.resolve(embed); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    @Test fun authenticatesBothPayloadLayoutsAndFiltersNonMediaAndUnsafeSources() {
        assertEquals(1, EkinoByseResolver.decode(encrypted(versioned = false), embed).size)
        val sources = """[{"url":"https://cdn.example/opaque","mime_type":"application/vnd.apple.mpegurl"},
            {"url":"https://127.0.0.1/x.mp4"},{"url":"http://cdn.example/x.mp4"},{"url":"https://cdn.example/advert.html"}]"""
        assertEquals("https://cdn.example/opaque", EkinoByseResolver.decode(encrypted(sources), embed).single().url)
        val broken = encrypted()
        (broken.path("playback") as com.fasterxml.jackson.databind.node.ObjectNode).put("iv", Base64.getEncoder().encodeToString(ByteArray(12)))
        try { EkinoByseResolver.decode(broken, embed); fail("Tampered payload must not emit sources") }
        catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun onlyRecognizesTheActualEmbedFrontendAndNeverPostsToOtherPaths() = runBlocking {
        val page = EkinoParser.document("<title>Byse Frontend</title><div id=root></div>")
        assertTrue(EkinoByseResolver.matches(page, embed))
        assertFalse(EkinoByseResolver.matches(EkinoParser.document("<title>Byse Frontend</title>"), embed))
        val resolver = EkinoByseResolver({ _, _, _, _ -> error("Invalid embed must not make a request") })
        for (url in listOf("http://byse.example/e/id", "https://byse.example/login", "https://user@byse.example/e/id",
            "https://byse.example/e/a%2Fb", "https://ekino.ws/e/id")) {
            assertFalse(EkinoByseResolver.matches(page, url))
            assertTrue(resolver.resolve(url).media.isEmpty())
        }
    }

    @Test fun publicPlayerWorkVectorIsBoundedAndCooperative() = runBlocking {
        assertEquals("169", EkinoByseWork.solve("validation-fixture", 8, 170))
        assertNull(EkinoByseWork.solve("validation-fixture", 8, 169))
        try { EkinoByseWork.solve("fixture", 256); fail("Expected bounded difficulty") }
        catch (_: IllegalArgumentException) { }
    }
}
