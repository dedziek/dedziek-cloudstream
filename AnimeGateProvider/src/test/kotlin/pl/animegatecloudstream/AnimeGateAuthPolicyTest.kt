package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateAuthPolicyTest {
    @Test fun authServiceAndItsDiscordCallbackAreAllowedWithoutGrantingCookieCapture() {
        val auth = "https://auth.animegate.pl/api/auth/discord/start"
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation(auth))
        assertTrue(AnimeGateAuthPolicy.allowLegacyNavigation(auth))
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fauth.animegate.pl%2Fapi%2Fauth%2Fdiscord%2Fcallback&state=PRIVATE"))
        assertFalse(AnimeGateAuthPolicy.canCapture(auth))
        assertFalse(AnimeGateAuthPolicy.isProbeUrl(auth))
        for (url in listOf("http://auth.animegate.pl/", "https://auth.animegate.pl.evil.com/", "https://other.animegate.pl/", "https://private@auth.animegate.pl/", "https://auth.animegate.pl:444/")) assertFalse(url, AnimeGateAuthPolicy.allowMainNavigation(url))
        assertFalse(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fauth.animegate.pl.evil.com%2Fcallback"))
    }
    @Test fun apiLoginAndItsDiscordCallbackAreAllowedWithoutGrantingCookieCapture() {
        val start = "https://api.animegate.pl/api/v1/auth/discord"
        val callback = "https://api.animegate.pl/api/v1/auth/discord/callback?code=SECRET&state=PRIVATE"
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation(start))
        assertTrue(AnimeGateAuthPolicy.allowLegacyNavigation(start))
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation(callback))
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fapi.animegate.pl%2Fapi%2Fv1%2Fauth%2Fdiscord%2Fcallback&state=PRIVATE"))
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation(AnimeGateAuthPolicy.ROOT))
        for (url in listOf(start, callback, "https://api.animegate.pl/", "https://api.animegate.pl/api/auth/session")) {
            assertFalse(AnimeGateAuthPolicy.isSite(url))
            assertFalse(AnimeGateAuthPolicy.canCapture(url))
            assertFalse(AnimeGateAuthPolicy.isProbeUrl(url))
            assertFalse(AnimeGateAuthPolicy.observedSessionApi(url))
        }
    }
    @Test fun apiLoginTrustDoesNotIncludeLookalikesOrOtherSubdomains() {
        for (url in listOf("http://api.animegate.pl/", "https://api.animegate.pl.evil.com/", "https://other.animegate.pl/", "https://private@api.animegate.pl/", "https://api.animegate.pl:444/")) {
            assertFalse(url, AnimeGateAuthPolicy.allowMainNavigation(url))
        }
        assertFalse(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fapi.animegate.pl.evil.com%2Fcallback"))
    }
    @Test fun backendRouteDiagnosticsRemainRedacted() {
        assertEquals("/api/{param}/auth/discord", AnimeGateAuthPolicy.safeApiPath("https://api.animegate.pl/api/v1/auth/discord?code=SECRET#PRIVATE"))
        assertEquals("/api/users/{param}", AnimeGateAuthPolicy.safeApiPath("https://api.animegate.pl/api/users/private-user?token=SECRET"))
        assertNull(AnimeGateAuthPolicy.safeApiPath("https://api.animegate.pl.evil.com/api/users/private-user"))
    }
    @Test fun allowsOnlyExactHttpsAuthOrigins() {
        for (url in listOf(AnimeGateAuthPolicy.LOGIN, "https://discord.com/login", "https://discordapp.com/oauth2/authorize", "https://animegate.pl:443/")) assertTrue(url, AnimeGateAuthPolicy.allowMainNavigation(url))
        for (url in listOf("http://animegate.pl/", "https://animegate.pl.evil.com/", "https://other.animegate.pl/", "https://evil@animegate.pl/", "https://animegate.pl:444/", "javascript:alert(1)", "https://127.0.0.1/", "intent://discord")) assertFalse(url, AnimeGateAuthPolicy.allowMainNavigation(url))
    }
    @Test fun acceptsOnlyAnimegateOAuthCallbacksIncludingEncodedParameterNames() {
        assertTrue(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fanimegate.pl%2Fapi%2Fauth%2Fcallback%2Fdiscord&state=private"))
        for (query in listOf("redirect_uri=https%3A%2F%2Fevil.com%2F", "redirect%5furi=https%3A%2F%2Fevil.com%2F", "redirect_uri=https%3A%2F%2Fanimegate.pl%2F&redirect_uri=https%3A%2F%2Fevil.com%2F")) assertFalse(AnimeGateAuthPolicy.allowMainNavigation("https://discord.com/oauth2/authorize?$query"))
    }
    @Test fun neverCapturesCredentialsOnDiscordOrDuringCallbacks() {
        assertTrue(AnimeGateAuthPolicy.canCapture(AnimeGateAuthPolicy.ROOT))
        for (url in listOf("https://discord.com/", AnimeGateAuthPolicy.LOGIN, "https://animegate.pl/api/auth/callback/discord?code=secret", "https://animegate.pl/?co%64e=secret", "https://animegate.pl/?state=secret")) assertFalse(url, AnimeGateAuthPolicy.canCapture(url))
    }
    @Test fun frontendProbeIsLimitedToTwoCanonicalUrlsAndNotArbitrarySameSitePaths() {
        assertTrue(AnimeGateAuthPolicy.isProbeUrl(AnimeGateAuthPolicy.ROOT))
        assertTrue(AnimeGateAuthPolicy.isProbeUrl(AnimeGateAuthPolicy.SESSION))
        for (url in listOf("https://animegate.pl/anime/private", "https://animegate.pl/%61pi/auth/session", "https://animegate.pl/?token=secret", "https://discord.com/api/auth/session")) assertFalse(AnimeGateAuthPolicy.isProbeUrl(url))
    }
    @Test fun legacyFramesNeverWidenTheMainDocumentPolicy() {
        for (url in listOf("about:srcdoc", "https://newassets.hcaptcha.com/frame", "https://challenges.cloudflare.com/frame")) {
            assertTrue(AnimeGateAuthPolicy.allowLegacyNavigation(url)); assertFalse(AnimeGateAuthPolicy.allowMainNavigation(url))
        }
    }
    @Test fun blankInterstitialIsAllowedWithoutBecomingACookieOrigin() {
        for (url in listOf("about:blank", "about:blank#interstitial")) {
            assertTrue(AnimeGateAuthPolicy.allowMainNavigation(url))
            assertTrue(AnimeGateAuthPolicy.allowLegacyNavigation(url))
            assertFalse(AnimeGateAuthPolicy.isSite(url))
            assertFalse(AnimeGateAuthPolicy.canCapture(url))
            assertFalse(AnimeGateAuthPolicy.isProbeUrl(url))
        }
        for (url in listOf("about:blank?next=https://evil.com", "about:srcdoc", "about:blank/evil", "about:blank#\nsecret")) assertFalse(AnimeGateAuthPolicy.allowMainNavigation(url))
    }
    @Test fun reportPathsRemoveValuesQueriesAndUnrecognisedSegments() {
        val path = AnimeGateAuthPolicy.safeApiPath("https://animegate.pl/api/users/private-user-123?access_token=SECRET#code")
        assertEquals("/api/users/{param}", path)
        assertFalse(path!!.contains("SECRET")); assertFalse(path.contains("private-user"))
        assertNull(AnimeGateAuthPolicy.safeApiPath("https://discord.com/api/users/private"))
        assertTrue(AnimeGateAuthPolicy.observedSessionApi("https://animegate.pl/api/auth/session?refresh=true"))
        assertFalse(AnimeGateAuthPolicy.observedSessionApi("https://evil.com/api/auth/session"))
    }
}
