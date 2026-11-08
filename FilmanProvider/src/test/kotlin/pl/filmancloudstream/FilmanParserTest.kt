package pl.filmancloudstream

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/** Network-free regression cases for the site's current layouts and untrusted source data. */
class FilmanParserTest {
    private val base = FilmanParser.BASE
    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/fixtures/$name.html")) {
        "Missing test resource: $name"
    }.readText(Charsets.UTF_8)
    private fun doc(html: String) = FilmanParser.document(html, base)
    private fun encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun row(attributes: String) = "<table id='links'><tr><td><a $attributes><img alt='host.example'></a></td><td>Lektor</td><td>720p</td></tr></table>"

    @Test fun permitsOnlyExactHttpsFilmanOriginForAuthenticatedRequests() {
        listOf(base, "$base/filmy/?page=2", "https://filman.cc:443/m/test").forEach {
            assertTrue(it, FilmanParser.isSiteUrl(it))
        }
    }

    @Test fun rejectsOriginConfusionAndNonHttpsAuthenticatedDestinations() {
        listOf("http://filman.cc/", "https://filman.cc.evil.example/", "https://evilfilman.cc/",
            "https://filman.cc@evil.example/", "https://name@filman.cc/", "https://filman.cc:444/",
            "https://filman.cc./", "file:///filman.cc", "//filman.cc/m/test", "not a URL").forEach {
            assertFalse(it, FilmanParser.isSiteUrl(it))
        }
    }

    @Test fun resolvesRelativeAndProtocolRelativePublicUrls() {
        assertEquals("$base/e/show/123/test/0", FilmanParser.absolute("$base/s/12/show", "/e/show/123/test/0"))
        assertEquals("https://media.example/video.mp4", FilmanParser.absolute(base, "//media.example/video.mp4"))
        assertEquals("https://media.example/a%20b.mp4", FilmanParser.absolute(base, " https://media.example/a%20b.mp4 "))
    }

    @Test fun rejectsScriptsCredentialsLocalAddressesAndUnexpectedPorts() {
        listOf("javascript:alert(1)", "data:text/html,hello", "file:///etc/passwd", "ftp://media.example/file",
            "https://name:password@media.example/file", "http://localhost/video", "http://127.0.0.1/video",
            "http://192.168.1.1/video", "http://[::1]/video", "http://router.local/video",
            "http://intranet/video", "https://media.example:9000/video", "https://media.example/\u0000").forEach {
            assertNull(it, FilmanParser.absolute(base, it))
        }
    }

    @Test fun rejectsLocalhostSubdomainsAndTrailingDotLocalDomains() {
        listOf("https://video.localhost/file", "https://router.local./file", "https://localhost./file").forEach {
            assertNull(it, FilmanParser.absolute(base, it))
        }
    }

    @Test fun identifiesLoginFormAndTitleEvenWhenHttpStatusWas200() {
        assertTrue(FilmanParser.isLoginPage(doc(fixture("login"))))
        assertTrue(FilmanParser.isLoginPage(doc("<title> logowanie - Filman </title>")))
        assertTrue(FilmanParser.isLoginPage(doc("<form id='signin-form'></form>")))
        assertFalse(FilmanParser.isLoginPage(doc(fixture("catalog-movies"))))
    }

    @Test fun distinguishesChallengePagesFromOrdinaryCatalog() {
        listOf("<title>Just a moment...</title>", "<title>Attention Required!</title>",
            "<form id='challenge-form'></form>", "<div id='cf-challenge-running'></div>").forEach {
            assertTrue(FilmanParser.isChallenge(doc(it)))
        }
        assertFalse(FilmanParser.isChallenge(doc(fixture("catalog-movies"))))
    }

    @Test fun parsesCurrentMovieCatalogAndPrefersLazyPosterOverPlaceholder() {
        val cards = FilmanParser.cards(doc(fixture("catalog-movies")), base)
        assertEquals(2, cards.size)
        assertEquals("Film próbny / Test Movie", cards[0].title)
        assertEquals("$base/m/film-probny-2026", cards[0].url)
        assertEquals("$base/public/static/poster/big/fixture.jpg", cards[0].poster)
        assertEquals(2026, cards[0].year)
        assertFalse(cards[0].series)
        assertEquals(2024, cards[1].year)
    }

    @Test fun parsesSeriesCatalogWithoutMovieItemClassOrYear() {
        val card = FilmanParser.cards(doc(fixture("catalog-series")), base).single()
        assertTrue(card.series)
        assertEquals("Serial próbny / Test Series", card.title)
        assertEquals("$base/s/12/serial-probny", card.url)
        assertNull(card.year)
        assertEquals("$base/public/static/poster/big/series-fixture.jpg", card.poster)
    }

    @Test fun parsesAjaxSearchLayout() {
        val html = "<a href='/m/film-2024'><img src='/poster.jpg'><div class='title'>Wynik &amp; tytuł</div><div class='year'>2024</div></a>"
        val card = FilmanParser.cards(doc(html), base).single()
        assertEquals("Wynik & tytuł", card.title)
        assertEquals(2024, card.year)
    }

    @Test fun dropsForeignCatalogEntriesAndDeduplicatesExactUrls() {
        val html = "<a href='/m/test' title='Jeden'></a><a href='/m/test' title='Powtórzony'></a>" +
            "<a href='https://filman.cc.evil.example/m/test' title='Obcy'></a><a href='/premium' title='Premium'></a>"
        assertEquals(listOf("Jeden"), FilmanParser.cards(doc(html), base).map { it.title })
    }

    @Test fun detailUsesCurrentTitleWithoutOnlineBadgeAndMetadata() {
        val detail = FilmanParser.details(doc(fixture("details-series")), "$base/s/12/show")
        assertEquals("Serial próbny", detail.title)
        assertEquals(2025, detail.year)
        assertEquals("Opis & szczegóły serialu próbnego.", detail.plot)
        assertEquals("$base/public/static/poster/big/series-fixture.jpg", detail.poster)
        assertEquals(listOf("Dramat"), detail.tags)
    }

    @Test fun episodesSortNumericallyBeyond99AndDropDuplicatesAndForeignLinks() {
        val episodes = FilmanParser.details(doc(fixture("details-series")), base).episodes
        assertEquals(listOf(1 to 2, 1 to 99, 1 to 120, 2 to 2, 2 to 10), episodes.map { it.season to it.episode })
        assertEquals("Sto dwudziesty", episodes[2].title)
        assertTrue(episodes.all { FilmanParser.isSiteUrl(it.url) })
    }

    @Test fun legacyMovieDetailsRemainReadableWithoutEpisodes() {
        val detail = FilmanParser.details(doc("<span itemprop='title'>Starszy film</span><div class='info'><ul><li>2020</li></ul></div><p class='description'>Opis.</p>"), base)
        assertEquals("Starszy film", detail.title)
        assertEquals(2020, detail.year)
        assertTrue(detail.episodes.isEmpty())
    }

    @Test fun currentPaginationUsesNextPageInsteadOfLastPage() {
        assertEquals("$base/filmy/?page=2", FilmanParser.nextPage(doc(fixture("pagination")), "$base/filmy/?page=1"))
    }

    @Test fun paginationSupportsRelNextAndRejectsForeignOrigin() {
        assertEquals("$base/seriale/?page=3", FilmanParser.nextPage(doc("<a rel='next' href='?page=3'>Dalej</a>"), "$base/seriale/?page=2"))
        assertNull(FilmanParser.nextPage(doc("<a rel='next' href='https://evil.example/page/3'>Dalej</a>"), base))
        assertNull(FilmanParser.nextPage(doc("<div>Brak kolejnej strony</div>"), base))
    }

    @Test fun readsBothCurrentAndLegacyRouteVariableNames() {
        assertEquals("fixture-route-token", FilmanParser.routeToken(fixture("players")))
        assertEquals("legacy", FilmanParser.routeToken("let rt = \"legacy\";"))
        assertEquals("constant", FilmanParser.routeToken("const routeToken = 'constant';"))
        assertNull(FilmanParser.routeToken("var unrelated = 'value';"))
    }

    @Test fun csrfPrefersFormTokenThenMetaAndSupportsMissingValues() {
        assertEquals("fixture-form", FilmanParser.csrf(doc(fixture("login"))))
        assertEquals("meta-only", FilmanParser.csrf(doc("<meta name='csrf-token' content='meta-only'>")))
        assertNull(FilmanParser.csrf(doc("<html></html>")))
    }

    @Test fun currentPlayerRowsProduceTokenRoutesAndLanguageQualityLabels() {
        val players = FilmanParser.players(doc(fixture("players")), "$base/m/test")
        assertEquals(2, players.size)
        assertEquals("$base/link/go/1001", players[0].url)
        assertEquals("video.example · Lektor · 720p", players[0].label)
        assertEquals("$base/link/go/1002", players[1].url)
    }

    @Test fun timedPremiumPreviewAndPremiumAdvertAreNotExposedAsFreeStreams() {
        val players = FilmanParser.players(doc(fixture("players")), base)
        assertFalse(players.any { it.url.contains("1003") || it.label.contains("Premium") })
    }

    @Test fun playerNamesIncludeEverySiteVersionWithoutGuessingOrNormalizingIt() {
        listOf("ENG", "Lektor", "Lektor_AI", "Napisy", "Napisy_Transl", "Napisy_Tansl", "Dubbing", "Inna wersja").forEach { version ->
            val html = row("data-id='42'").replace("<td>Lektor</td>", "<td>$version</td>")
            val player = FilmanParser.players(doc(html), base).single()
            assertEquals(version, player.version)
            assertEquals("Voe · $version · 720p", player.displayName("Voe"))
        }
    }

    @Test fun versionCanComeFromRowClassWhenItsCellIsEmpty() {
        val html = row("data-id='42'").replace("<tr>", "<tr class='version-Lektor_AI version'>")
            .replace("<td>Lektor</td>", "<td></td>")
        val player = FilmanParser.players(doc(html), base).single()
        assertEquals("Host · Lektor_AI · 720p", player.displayName("Host"))
    }

    @Test fun missingVersionIsNotInventedAndHostingFallbackRemainsReadable() {
        val player = FilmanParser.players(doc(row("data-id='42'").replace("<td>Lektor</td>", "<td></td>")), base).single()
        assertEquals("Host · 720p", player.displayName("Host"))
        assertEquals("host.example · 720p", player.displayName(""))
    }

    @Test fun sameHostUrlWithDifferentVersionsRemainsTwoSources() {
        val html = row("data-id='42'") + row("data-id='42'").replace("<td>Lektor</td>", "<td>Napisy</td>")
        val players = FilmanParser.players(doc(html), base)
        assertEquals(listOf("Lektor", "Napisy"), players.map { it.version })
    }

    @Test fun legacyIframeRowsSupportEncodedHtmlAndEncodedJson() {
        val html = row("data-iframe='${encode("<iframe src=\"https://video.example/embed/one\"></iframe>")}'") +
            row("data-iframe='${encode("{\"src\":\"https:\\/\\/second.example\\/embed\\/two\"}")}'")
        assertEquals(listOf("https://video.example/embed/one", "https://second.example/embed/two"),
            FilmanParser.players(doc(html), base).map { it.url })
    }

    @Test fun skipsMalformedLegacyPlayerDataAndDeduplicatesRoutes() {
        val html = row("data-iframe='%%%'") + row("data-id='42'") + row("data-link-id='42'")
        assertEquals(listOf("$base/link/go/42"), FilmanParser.players(doc(html), base).map { it.url })
    }

    @Test fun embeddedUrlsAcceptDirectIframeVideoAndJsonPayloads() {
        assertEquals("https://video.example/file.mp4", FilmanParser.embeddedUrl("https://video.example/file.mp4", base))
        assertEquals("https://video.example/file.mp4", FilmanParser.embeddedUrl("<video><source src='https://video.example/file.mp4'></video>", base))
        assertEquals("https://video.example/embed", FilmanParser.embeddedUrl("{'url':'//video.example/embed'}", base))
    }

    @Test fun embeddedUrlsRejectJavascriptAndLocalNetworkDestinations() {
        listOf("<iframe src='javascript:alert(1)'>", "{'src':'http://127.0.0.1/secret'}", "not a link", "data:text/html,hello").forEach {
            assertNull(it, FilmanParser.embeddedUrl(it, base))
        }
    }

    @Test fun base64SupportsUtf8WhitespaceMissingPaddingAndUrlSafeAlphabet() {
        val input = "Źródło próbne"
        assertEquals(input, FilmanParser.decodeBase64(encode(input)))
        assertEquals("test", FilmanParser.decodeBase64(" dG Vz dA==\n"))
        assertEquals("test", FilmanParser.decodeBase64("dGVzdA"))
        assertEquals("~~~", FilmanParser.decodeBase64("fn5-"))
    }

    @Test fun base64RejectsMalformedAndOversizedInput() {
        listOf("", "A", "a===", "AA=A", "%%%", "dGVzdA=", "=".repeat(4)).forEach {
            assertNull(it, FilmanParser.decodeBase64(it))
        }
        assertNull(FilmanParser.decodeBase64("A".repeat(1_000_001)))
    }

    @Test fun unwrapsCurrentXorHostWrapperWithoutExecutingJavascript() {
        val target = "https://video.example/embed/test"
        val key = "abc123".toByteArray(Charsets.UTF_8)
        val bytes = target.toByteArray(Charsets.UTF_8)
        val payload = Base64.getEncoder().encodeToString(ByteArray(bytes.size) { (bytes[it].toInt() xor key[it % key.size].toInt()).toByte() })
        val html = "var _e = '$payload'; var _a = 'ab'; var _b = 'c1'; var _c = '23';"
        assertEquals(target, FilmanParser.unwrapHost(html, "https://embed.tmp-url.pro/fixture"))
    }

    @Test fun unwrapsLegacyBase64Rot13HostWrapper() {
        val rot13 = "uggcf://ivqrb.rknzcyr/rzorq/grfg"
        assertEquals("https://video.example/embed/test", FilmanParser.unwrapHost("var _e = '${encode(rot13)}';", base))
    }

    @Test fun wrapperRejectsMalformedDataMissingPayloadAndUnsafeDecodedTarget() {
        assertNull(FilmanParser.unwrapHost("var _e = '%%%';", base))
        assertNull(FilmanParser.unwrapHost("var unrelated = 'test';", base))
        assertNull(FilmanParser.unwrapHost("var _e = '${encode("uggc://127.0.0.1/frperg")}';", base))
    }
}
