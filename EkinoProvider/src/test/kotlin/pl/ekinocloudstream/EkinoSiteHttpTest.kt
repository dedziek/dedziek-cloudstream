package pl.ekinocloudstream

import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class EkinoSiteHttpTest {
    @Test fun reproducesConflictingUserAgentsInTheOldSharedRequestsClient() {
        val old = requestCreator("GET", "https://ekino.ws/", headers =
            mapOf("user-agent" to "CloudStream-default") + mapOf("User-Agent" to "Saved-WebView"))
        assertEquals(listOf("CloudStream-default", "Saved-WebView"), old.headers.values("User-Agent"))
    }

    @Test fun theWireReceivesOneSavedIdentityAndOnlyTheOperationsCookies() = runBlocking {
        var sharedJarReads = 0
        var sharedJarWrites = 0
        val sharedJar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                sharedJarReads++
                return listOf(Cookie.Builder().name("PHPSESSID").value("stale-global-session").domain(url.host).build())
            }
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { sharedJarWrites++ }
        }
        val base = OkHttpClient.Builder().cookieJar(sharedJar).build()
        val http = EkinoSiteHttp.isolated(base)
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("ok").addHeader("Set-Cookie", "PHPSESSID=new-session; Path=/"))
            val response = http.get(server.url("/watch/f/mix/fixture").toString(), headers = mapOf(
                "User-Agent" to "Saved-WebView", "Cookie" to "PHPSESSID=operation-session", "Referer" to "https://ekino.ws/"),
                allowRedirects = false)
            assertEquals("ok", response.text)
            val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertEquals(listOf("Saved-WebView"), request.headers.values("User-Agent"))
            assertEquals("PHPSESSID=operation-session", request.getHeader("Cookie"))
            assertEquals(0, sharedJarReads)
            assertEquals(0, sharedJarWrites)
            assertSame(sharedJar, base.cookieJar) // The host app is unchanged.
            assertFalse(http.baseClient.retryOnConnectionFailure)
        } finally { server.shutdown() }
    }

    @Test fun redirectIsReturnedWithoutAFollowupRequestOrLeakingCredentials() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/other-origin")))
            val http = EkinoSiteHttp.isolated(OkHttpClient())
            val response = http.get(server.url("/watch/f/mix/fixture").toString(), headers = mapOf(
                "User-Agent" to "Saved-WebView", "Cookie" to "PHPSESSID=operation-session"), allowRedirects = false)
            assertEquals(302, response.code)
            response.body.close()
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
