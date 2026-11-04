package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateRedirectDiagnosticsTest {
    private val source = AnimeGateRedirectDiagnostics.Source.MAIN_NAVIGATION

    @Test fun reportsTheMissingDestinationWithoutOAuthValuesOrPersonalPaths() {
        val trace = AnimeGateRedirectDiagnostics()
        val block = trace.record("https://auth.example.com/auth/private-person/callback?code=SECRET_CODE&state=SECRET_STATE#SECRET_FRAGMENT", source)!!
        assertEquals(AnimeGateAuthPolicy.BlockReason.HOST_NOT_ALLOWED, block.reason)
        val report = trace.report()
        assertTrue(report.contains("first_block_host=auth.example.com"))
        assertTrue(report.contains("first_block_path=/auth/{param}/callback"))
        assertFalse(report.contains("SECRET")); assertFalse(report.contains("private-person"))
        assertFalse(block.toString().contains("SECRET"))
    }
    @Test fun distinguishesAnUntrustedOAuthCallbackFromABlockedDiscordHost() {
        val trace = AnimeGateRedirectDiagnostics()
        val block = trace.record("https://discord.com/oauth2/authorize?client_id=PRIVATE_ID&state=SECRET&redirect_uri=https%3A%2F%2Fauth.example.com%2Fcallback%3Fcode%3DPRIVATE_CODE", source)!!
        assertEquals(AnimeGateAuthPolicy.BlockReason.OAUTH_CALLBACK_ORIGIN, block.reason)
        assertEquals("discord.com", block.host)
        assertEquals("auth.example.com", block.callbackHost)
        assertFalse(trace.report().contains("PRIVATE")); assertFalse(trace.report().contains("SECRET"))
    }
    @Test fun allowedNavigationDoesNotEraseTheFirstBlock() {
        val trace = AnimeGateRedirectDiagnostics()
        assertNull(trace.record("about:blank", source))
        assertNull(trace.record(AnimeGateAuthPolicy.LOGIN, source))
        trace.record("https://first.example.com/login?token=SECRET", source)
        assertNull(trace.record("https://discord.com/login", source))
        trace.record("https://second.example.com/login", AnimeGateRedirectDiagnostics.Source.MAIN_REQUEST)
        val report = trace.report()
        assertTrue(report.contains("blocked_count=2"))
        assertTrue(report.contains("first_block_host=first.example.com"))
        assertTrue(report.contains("last_block_host=second.example.com"))
        assertTrue(report.contains("last_block_source=MAIN_REQUEST"))
    }
    @Test fun malformedAndExternalSchemesCannotLeakRawValues() {
        val trace = AnimeGateRedirectDiagnostics()
        for (url in listOf("SECRET_SCHEME:SECRET_TOKEN", "https://SECRET_USER:SECRET_PASSWORD@external.example.com/auth?token=SECRET", "https://external.example.com/login?broken=%ZZ&state=SECRET", "https://external.example.com/\nSECRET")) {
            val block = trace.record(url, source)!!
            assertFalse(block.toString().contains("SECRET"))
            assertFalse(trace.report().contains("SECRET"))
        }
        assertEquals(AnimeGateAuthPolicy.BlockReason.UNSUPPORTED_PORT, AnimeGateAuthPolicy.mainBlockReason("https://discord.com:444/login"))
        assertEquals(AnimeGateAuthPolicy.BlockReason.MALFORMED_OAUTH, AnimeGateAuthPolicy.mainBlockReason("https://discord.com/oauth2/authorize?redirect%ZZ=SECRET"))
    }
    @Test fun subdomainsAndForeignCallbacksRemainBlocked() {
        for (url in listOf("https://www.animegate.pl/", "https://discord.com.evil.example/login", "https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fexternal.example%2F", "intent://discord.com/login#Intent;scheme=https;end")) {
            assertNotNull(AnimeGateRedirectDiagnostics().record(url, source))
            assertFalse(AnimeGateAuthPolicy.allowMainNavigation(url))
        }
    }
}
