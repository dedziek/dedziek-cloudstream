package pl.ogladajanimecloudstream

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class OgladajAnimeSiteTest {
    private val home = "https://ogladajanime.pl/"
    private val search = "${home}manager.php?action=get_search"
    private val form = mapOf("search" to "anime")

    private fun response(url: String, status: Int = 200, vararg headers: Pair<String, String>) = Response.Builder()
        .request(Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
        .body("fixture".toResponseBody()).apply { headers.forEach { (name, value) -> addHeader(name, value) } }.build()

    @Test fun selectsPhpSessionRotatesItAndRefreshesItOnceAfterExpiry() = runBlocking {
        val calls = mutableListOf<Pair<String, String?>>()
        val site = OgladajAnimeSite { url, _, headers ->
            calls.add(url to headers["Cookie"])
            when (calls.size) {
                1 -> response(url, 200, "Set-Cookie" to "analytics=no; Path=/", "Set-Cookie" to "PHPSESSID=first; Path=/")
                2 -> response(url, 200, "Set-Cookie" to "PHPSESSID=rotated; Path=/")
                3 -> response(url, 419)
                4 -> response(url, 200, "Set-Cookie" to "PHPSESSID=fresh; Path=/")
                else -> response(url)
            }
        }
        site.request(search, form)
        assertEquals("fixture", site.request(search, form).text)
        assertEquals(listOf(home to null, search to "PHPSESSID=first", search to "PHPSESSID=rotated",
            home to null, search to "PHPSESSID=fresh"), calls)
    }

    @Test fun relativePagesResolveLocallyAndRedirectsDoNotShareCookiesBetweenHosts() = runBlocking {
        val calls = mutableListOf<Pair<String, String?>>()
        val alias = "https://www.ogladajanime.pl/anime/example"
        val site = OgladajAnimeSite { url, _, headers ->
            calls.add(url to headers["Cookie"])
            when (url) {
                home -> response(url, 200, "Set-Cookie" to "PHPSESSID=local; Domain=ogladajanime.pl; Path=/")
                "${home}anime/example" -> response(url, 302, "Location" to alias)
                else -> response(url)
            }
        }
        assertEquals(alias, site.request("/anime/example").url)
        assertEquals(listOf(home to null, "${home}anime/example" to "PHPSESSID=local", alias to null), calls)
    }

    @Test fun rejectsOtherOriginsBeforeFetchingOrFollowingRedirects() = runBlocking {
        val rejected = listOf("https://example.org/a", "//example.org/a", "http://ogladajanime.pl/a",
            "https://ogladajanime.pl:8443/a", "https://name@ogladajanime.pl/a")
        for (url in rejected) {
            var calls = 0
            val site = OgladajAnimeSite { target, _, _ -> calls++; response(target, 302, "Location" to url) }
            try { site.request(url); fail("Accepted an untrusted URL") } catch (_: OgladajAnimeSite.Problem) { }
            assertEquals(0, calls)
            try { site.request(home); fail("Followed an untrusted redirect") } catch (_: OgladajAnimeSite.Problem) { }
            assertEquals(1, calls)
        }
    }

    @Test fun searchHttpFailureIsReportedWithoutRetryingAServiceDenial() = runBlocking {
        val calls = mutableListOf<String>()
        val site = OgladajAnimeSite { url, _, _ -> calls.add(url); response(url, if (url == search) 429 else 200) }
        try { site.request(search, form); fail("The denial looked like a successful search") }
        catch (error: OgladajAnimeSite.Problem) { assertTrue(error.message.orEmpty().contains("HTTP 429")) }
        assertEquals(listOf(home, search), calls)
    }
}
