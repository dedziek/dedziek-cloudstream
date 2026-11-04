package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateCatalogContractTest {
    @Test fun reportsOnlyRoutePatternsQueryNamesAndItemFieldTypes() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/PRIVATE_ID/episodes?page=3&q=PRIVATE_QUERY")
        val request = catalog.requests().single()
        assertFalse(request.toString().contains("PRIVATE"))
        val result = catalog.add(request, 200, """{"success":true,"data":{"items":[{"id":"PRIVATE_ID","title":"PRIVATE_TITLE","posterUrl":"https://PRIVATE_HOST/?token=PRIVATE_TOKEN"}],"pagination":{"page":3,"total":8}},"PRIVATE_KEY":"PRIVATE_VALUE"}""")
        val report = catalog.report()
        assertTrue(report.contains("/api/v1/anime/{param}/episodes"))
        assertTrue(report.contains("query_keys=page,q"))
        assertTrue(result.shape.contains("array[{id:string,title:string,posterUrl:string}]"))
        assertTrue(result.shape.contains("pagination:{page:number,total:number}"))
        assertFalse(report.contains("PRIVATE")); assertFalse(result.toString().contains("PRIVATE"))
        assertFalse(report.contains("https://"))
    }
    @Test fun excludesOtherOriginsPrivateRoutesCredentialQueriesAndNonGetRequests() {
        val catalog = AnimeGateCatalogContract()
        val urls = listOf("http://api.animegate.pl/api/v1/anime", "https://api.animegate.pl.evil.com/api/v1/anime", "https://auth.animegate.pl/api/v1/anime", "https://private@api.animegate.pl/api/v1/anime", "https://api.animegate.pl:444/api/v1/anime", "https://api.animegate.pl/api/v1/auth/me", "https://api.animegate.pl/api/v1/me/history/anime", "https://api.animegate.pl/api/v1/anime/PRIVATE/watchlist", "https://api.animegate.pl/api/v1/anime?access_token=PRIVATE", "https://api.animegate.pl/api/v1/anime?%61ccess_token=PRIVATE", "https://api.animegate.pl/api/v1/anime#PRIVATE", "https://api.animegate.pl/api/v1/anime/%2e%2e/auth")
        for (url in urls) { assertFalse(url, AnimeGateCatalogContract.eligible(url)); catalog.observe("GET", url) }
        for (url in listOf("https://api.animegate.pl/api/v1/anime/PRIVATE/delete", "https://api.animegate.pl/api/v1/anime/PRIVATE/FOLLOW", "https://api.animegate.pl/api/v1/anime?api_key=PRIVATE")) {
            assertFalse(url, AnimeGateCatalogContract.eligible(url)); catalog.observe("GET", url)
        }
        catalog.observe("POST", "https://api.animegate.pl/api/v1/anime")
        assertTrue(catalog.requests().isEmpty())
    }
    @Test fun usesLatestSearchAndFreezesItForTheActiveTest() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/search?q=FIRST")
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/search?q=LATEST")
        val request = catalog.requests().single()
        assertTrue(request.url.endsWith("LATEST"))
        catalog.start()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/search?q=IGNORED")
        assertSame(request, catalog.requests().single())
        catalog.add(request, 200, "{}"); catalog.finish("DONE")
        assertTrue(catalog.report().contains("catalog_state=DONE"))
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/search?q=NEW")
        assertNotSame(request, catalog.requests().single())
        assertThrows(IllegalArgumentException::class.java) { catalog.add(request, 200, "{}") }
    }
    @Test fun rejectsForgedRequestsAndHeadersBeforeOpeningANetworkConnection() {
        val catalog = AnimeGateCatalogContract()
        val forged = AnimeGateCatalogContract.Request("https://evil.example/", "/api/v1/anime", "NONE")
        assertThrows(IllegalArgumentException::class.java) { catalog.request(forged, "PRIVATE_COOKIE", "WebView") }
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime")
        val request = catalog.requests().single()
        assertThrows(IllegalArgumentException::class.java) { catalog.request(request, "PRIVATE\r\nInjected: yes", "WebView") }
        assertThrows(IllegalArgumentException::class.java) { catalog.request(request, "PRIVATE", "WebView\nInjected") }
    }
    @Test fun observationBatchAndSchemaSizesAreBounded() {
        val catalog = AnimeGateCatalogContract()
        for (i in 1..40) catalog.observe("GET", "https://api.animegate.pl/api/v$i/anime")
        assertEquals(8, catalog.requests().size)
        assertTrue(catalog.report().contains("catalog_observed=24"))
        val body = (1..100).joinToString(",", "{", "}") { "\"PRIVATE_$it\":{\"data\":[{\"items\":[{\"title\":\"PRIVATE_VALUE\"}]}]}" }
        val result = catalog.add(catalog.requests().first(), 200, body)
        assertTrue(result.shape.length <= 3500); assertFalse(result.shape.contains("PRIVATE"))
    }
    @Test fun malformedHtmlTruncatedAndEmptyResponsesNeverExposeTheirContents() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime")
        val request = catalog.requests().single()
        assertEquals("html", catalog.add(request, 403, "<html>PRIVATE</html>").shape)
        assertEquals("non_json", catalog.add(request, 200, "PRIVATE_TOKEN").shape)
        assertEquals("truncated", catalog.add(request, 200, "PRIVATE_DATA", true).shape)
        assertEquals("empty", catalog.add(request, 0, "").shape)
        assertFalse(catalog.report().contains("PRIVATE"))
    }
}
