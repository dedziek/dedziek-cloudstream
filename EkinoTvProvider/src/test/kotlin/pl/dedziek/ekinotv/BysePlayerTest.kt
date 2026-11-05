package pl.dedziek.ekinotv

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BysePlayerTest {
    private val mapper = ObjectMapper()
    private val embed = "https://hosting.example/e/movie?temporary=PRIVATE"
    private val task = """{"pow_nonce":"validation-fixture","pow_difficulty":8,"pow_token":"TASK_SECRET"}"""
    private fun payload(version: Int? = 5): com.fasterxml.jackson.databind.node.ObjectNode {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val key = ByteArray(32) { (it * 3).toByte() }; val iv = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val data = """{"sources":[{"url":"https://cdn.example/master.m3u8?token=MEDIA_SECRET","mime_type":"application/vnd.apple.mpegurl","height":1080},
            {"url":"https://127.0.0.1/secret.mp4"},{"url":"https://ekino.ws/private.mp4"},{"url":"https://cdn.example/ad.html"}]}"""
        val parts = if (version == null) listOf(encoder.encodeToString(key)) else MutableList(30) {
            encoder.encodeToString(ByteArray(16) { 99 })
        }.apply { this[4] = encoder.encodeToString(key.copyOfRange(0, 16)); this[25] = encoder.encodeToString(key.copyOfRange(16, 32)) }
        val envelope = mapper.createObjectNode().put("algorithm", "AES-256-GCM").put("iv", encoder.encodeToString(iv))
            .put("payload", encoder.encodeToString(cipher.doFinal(data.toByteArray())))
        envelope.set<com.fasterxml.jackson.databind.JsonNode>("key_parts", mapper.valueToTree(parts))
        if (version != null) envelope.put("version", version)
        return mapper.createObjectNode().set("playback", envelope)
    }

    @Test fun publicProtocolUsesExactlyThreePostsAndCorrectlyReadsVersionFiveSources() = runBlocking {
        val routes = mutableListOf<String>()
        val player = BysePlayer({ url, parent, body, token ->
            assertEquals(embed, parent); assertFalse(url.contains("PRIVATE"))
            routes.add(url.substringAfter("/embed/"))
            val json = mapper.readTree(body)
            when (routes.size) {
                1 -> { assertNull(token); HostResponse(200, task) }
                2 -> {
                    assertEquals("169", json.path("solution").asText()); assertEquals("TASK_SECRET", json.path("pow_token").asText())
                    HostResponse(200, """{"status":"ok","token":"ACCESS_SECRET"}""")
                }
                3 -> { assertEquals("ACCESS_SECRET", token); HostResponse(200, payload().toString()) }
                else -> error("No retries permitted")
            }
        })
        val result = player.sources(embed)
        assertEquals(listOf("captcha", "captcha/verify", "playback"), routes)
        assertEquals(1, result.media.size); assertEquals(1080, result.media.single().quality)
        assertEquals(mapOf("Origin" to "https://hosting.example"), result.media.single().headers)
        assertFalse(result.message.contains("SECRET"))
    }

    @Test fun accessDenialNeverTriggersWorkOrAnotherRequest() = runBlocking {
        for (status in listOf(401, 403, 429)) {
            var calls = 0
            val result = BysePlayer({ _, _, _, _ -> calls++; HostResponse(status, "") }, { _, _ -> error("No work after denial") }).sources(embed)
            assertTrue(result.blocked); assertEquals(1, calls); assertTrue(result.media.isEmpty())
        }
    }

    @Test fun excessiveWorkAndRejectedVerificationStopBeforePlayback() = runBlocking {
        var calls = 0
        BysePlayer({ _, _, _, _ -> calls++; HostResponse(200, task.replace(":8", ":24")) }, { _, _ -> error("Unbounded work") }).sources(embed)
        assertEquals(1, calls)
        calls = 0
        BysePlayer({ _, _, _, _ -> calls++; HostResponse(200, if (calls == 1) task else "{\"status\":\"rejected\"}") }, { _, _ -> "169" }).sources(embed)
        assertEquals(2, calls)
    }

    @Test fun authenticationRejectsTamperingAndSupportsUnversionedPayloads() {
        assertEquals(1, BysePlayer.decode(payload(null), embed).size)
        val broken = payload()
        (broken.path("playback") as com.fasterxml.jackson.databind.node.ObjectNode).put("iv", Base64.getEncoder().encodeToString(ByteArray(12) { 1 }))
        try { BysePlayer.decode(broken, embed); fail("Tampered payload") } catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun workMatchesTheObservedPublicVectorAndHonorsCancellation() = runBlocking {
        assertEquals("169", PlayerWork.answer("validation-fixture", 8, 170))
        assertNull(PlayerWork.answer("validation-fixture", 8, 169))
        val result = async(start = CoroutineStart.LAZY) { PlayerWork.answer("test", 20) }
        result.cancel()
        try { result.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }

    @Test fun nativeHostingClientRefusesOfficialAndBridgeUrlsBeforeMakingAnyRequest() = runBlocking {
        val client = HostHttp("actual-browser-agent")
        for (url in listOf("https://ekino.ws/watch/f/player/id", "https://ekino-tv.pl/", "https://play.ekino.link/x", "http://host.example/e/id")) {
            try { client.request(url, embed); fail(url) } catch (_: IllegalArgumentException) { }
        }
    }
}
