package pl.animegatecloudstream

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AnimeGateMediaHttpTest {
    @Test fun ignoresTheRestOfALargeResponseEvenWhenTheServerIgnoresRange() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody("x".repeat(100_000)))
            val response = AnimeGateMediaHttp(OkHttpClient()).read(server.url("/stream").toString(), mapOf("Range" to "bytes=0-8191", "Referer" to "https://playmogo.com/"))
            assertEquals(8192, response.prefix.size)
            val request = server.takeRequest()
            assertEquals("bytes=0-8191", request.getHeader("Range"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
}
