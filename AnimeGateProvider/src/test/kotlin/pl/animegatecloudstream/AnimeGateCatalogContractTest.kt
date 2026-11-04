package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateCatalogContractTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/animegate/$name.json")).readText()

    @Test fun checksObservedCatalogAndDetailsWithoutExportingTheirValues() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/catalog-search?mode=preview&page=1&limit=8&search=PRIVATE_QUERY&sortBy=rating&sortOrder=desc")
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/slug/fixture-anime")
        catalog.observe("GET", "https://api.animegate.pl/api/v1/episodes/anime/PRIVATE_ID?publicOnly=true")
        val requests = catalog.requests()
        catalog.add(requests[0], 200, fixture("catalog-preview"))
        catalog.add(requests[1], 200, fixture("anime-detail"))
        catalog.add(requests[2], 200, fixture("episodes"))
        val report = catalog.report()
        assertTrue(report.contains("/api/v1/anime/catalog-search"))
        assertTrue(report.contains("/api/v1/anime/slug/{param}"))
        assertTrue(report.contains("query_keys=limit,mode,page,search,sortBy,sortOrder"))
        assertTrue(report.contains("query_keys=publicOnly"))
        assertTrue(report.contains("catalog_1_parsed_anime=1"))
        assertTrue(report.contains("catalog_2_parsed_details=1"))
        assertTrue(report.contains("catalog_3_parsed_episodes=1"))
        for (value in listOf("PRIVATE", "fixture-anime", "Fixture Anime", "images.example", "anime-fixture-1")) {
            assertFalse(value, report.contains(value))
        }
    }

    @Test fun doesNotClaimCatalogSuccessForErrorsTruncationWrongSlugsOrOtherRoutes() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/catalog-search")
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/search-suggestions?q=PRIVATE")
        catalog.observe("GET", "https://api.animegate.pl/api/v1/anime/slug/wrong-slug")
        val requests = catalog.requests()
        assertEquals(-1, catalog.add(requests[0], 403, fixture("catalog-preview")).parsedAnime)
        assertEquals(-1, catalog.add(requests[0], 200, fixture("catalog-preview"), true).parsedAnime)
        assertEquals(-1, catalog.add(requests[1], 200, fixture("catalog-preview")).parsedAnime)
        assertEquals(-1, catalog.add(requests[2], 200, fixture("anime-detail")).parsedDetails)
        catalog.observe("POST", "https://api.animegate.pl/api/v1/episodes/PRIVATE/playback")
        assertEquals(3, catalog.requests().size)
    }

    @Test fun supportsObservedFrontendProxiesAndBackendNamespacesWithoutMixingOrigins() {
        val catalog = AnimeGateCatalogContract()
        val frontend = "https://animegate.pl/api/metadata?includeLinks=true&q=PRIVATE_QUERY"
        val backend = "https://api.animegate.pl/api/v1/metadata/anime?page=1"
        assertTrue(AnimeGateCatalogContract.eligible(frontend)); assertTrue(AnimeGateCatalogContract.eligible(backend))
        catalog.observe("GET", frontend); catalog.observe("GET", backend)
        assertEquals(setOf("SITE", "API"), catalog.requests().map { it.origin }.toSet())
        val request = catalog.requests().first { it.origin == "SITE" }
        val result = catalog.add(request, 200, """{"data":[{"id":"PRIVATE_ID","sourceName":"PRIVATE_NAME","sortOrder":3,"airDate":null,"user":{"PRIVATE_PERSON":"PRIVATE_EMAIL"}}]}""")
        assertEquals("SITE", result.origin)
        assertTrue(result.shape.contains("sourceName:string"))
        assertTrue(result.shape.contains("sortOrder:number"))
        assertTrue(result.shape.contains("airDate:null"))
        assertFalse(catalog.report().contains("PRIVATE"))
        assertTrue(catalog.report().contains("query_keys=includeLinks,q"))
        for (url in listOf("https://animegate.pl/api/auth/session", "https://animegate.pl/api/revalidate?secret=PRIVATE", "https://api.animegate.pl/api/v1/services/oauth/start", "https://api.animegate.pl/api/v1/my/anime", "https://animegate.pl/api/trpc/anime.search")) assertFalse(url, AnimeGateCatalogContract.eligible(url))
    }
    @Test fun confirmsEpisodeParserCountsWithoutExportingIdentifiers() {
        val catalog = AnimeGateCatalogContract()
        catalog.observe("GET", "https://api.animegate.pl/api/v1/episodes/anime/PRIVATE_ID?includeLinks=true")
        val request = catalog.requests().single()
        catalog.add(request, 200, """{"success":true,"data":[{"id":"PRIVATE_EPISODE","animeId":"PRIVATE_ID","number":1,"title":null,"links":[]}]}""")
        val report = catalog.report()
        assertTrue(report.contains("parsed_episodes=1"))
        assertFalse(report.contains("PRIVATE"))
    }
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
