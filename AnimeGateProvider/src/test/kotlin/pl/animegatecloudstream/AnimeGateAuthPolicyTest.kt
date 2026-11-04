package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateAuthPolicyTest {
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
    @Test fun probeIsLimitedToTwoCanonicalUrlsAndNotArbitrarySameSitePaths() {
        assertTrue(AnimeGateAuthPolicy.isProbeUrl(AnimeGateAuthPolicy.ROOT))
        assertTrue(AnimeGateAuthPolicy.isProbeUrl(AnimeGateAuthPolicy.SESSION))
        for (url in listOf("https://animegate.pl/anime/private", "https://animegate.pl/%61pi/auth/session", "https://animegate.pl/?token=secret", "https://discord.com/api/auth/session")) assertFalse(AnimeGateAuthPolicy.isProbeUrl(url))
    }
    @Test fun legacyFramesNeverWidenTheMainDocumentPolicy() {
        for (url in listOf("about:blank", "about:srcdoc", "https://newassets.hcaptcha.com/frame", "https://challenges.cloudflare.com/frame")) {
            assertTrue(AnimeGateAuthPolicy.allowLegacyNavigation(url)); assertFalse(AnimeGateAuthPolicy.allowMainNavigation(url))
        }
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
