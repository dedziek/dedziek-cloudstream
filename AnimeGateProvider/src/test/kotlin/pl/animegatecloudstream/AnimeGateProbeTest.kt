package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateProbeTest {
    @Test fun observedBackendSessionCanBeClassifiedWithoutFollowingAuthRedirects() {
        val backend = "https://api.animegate.pl/api/v7/auth/session"
        assertEquals(AnimeGateProbe.Outcome.VERIFIED_SESSION_API, AnimeGateProbe.classify(backend, 200, """{"user":{"id":"PRIVATE"}}""").outcome)
        assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateProbe.classify(backend, 200, "{}").outcome)
        assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateProbe.classify(backend, 200, "<html>Loading</html>").outcome)
        assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateProbe.classify(backend, 302, "", "https://auth.animegate.pl/api/auth/discord/start?state=SECRET").outcome)
    }
    @Test fun verifiesOnlyTheSessionApiAndDoesNotExposeItsPrivateFields() {
        val evidence = AnimeGateProbe.classify(AnimeGateAuthPolicy.SESSION, 200, """{"user":{"name":"SECRET","email":"private@example.com"}}""")
        assertEquals(AnimeGateProbe.Outcome.VERIFIED_SESSION_API, evidence.outcome)
        assertFalse(evidence.toString().contains("SECRET")); assertFalse(evidence.toString().contains("private@example"))
    }
    @Test fun emptyOrUnknownSessionResponsesAreNotAuthenticationProof() {
        assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateProbe.classify(AnimeGateAuthPolicy.SESSION, 200, "{}").outcome)
        for (body in listOf("<html>Loading</html>", "[]", """{"other":true}""", """{"error":"secret","user":{"name":"user"}}""")) assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateProbe.classify(AnimeGateAuthPolicy.SESSION, 200, body).outcome)
    }
    @Test fun cookieOrHtmlShellDoesNotProveLogin() {
        assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 200, "<div>Loading...</div>").outcome)
        val candidate = AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 200, "<a href='/logout'>Wyloguj</a><a href='/anime/example'>Anime</a>")
        assertEquals(AnimeGateProbe.Outcome.PAGE_CANDIDATE, candidate.outcome)
        assertNotEquals(AnimeGateProbe.Outcome.VERIFIED_SESSION_API, candidate.outcome)
    }
    @Test fun distinguishesDenialRateLimitAndLoginRedirectWithoutFollowingIt() {
        assertEquals(AnimeGateProbe.Outcome.ACCESS_DENIED, AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 403, "blocked").outcome)
        assertEquals(AnimeGateProbe.Outcome.RATE_LIMITED, AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 429, "wait").outcome)
        assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 307, "", "/auth/login?next=/").outcome)
        assertEquals(AnimeGateProbe.Outcome.REDIRECT_OTHER, AnimeGateProbe.classify(AnimeGateAuthPolicy.ROOT, 302, "", "https://evil.com/?code=SECRET").outcome)
    }
    @Test fun refusesCredentialRequestsToOtherUrlsBeforeOpeningTheNetwork() {
        assertThrows(IllegalArgumentException::class.java) { AnimeGateProbe.request("https://discord.com/", "secret=value", "WebView") }
        assertThrows(IllegalArgumentException::class.java) { AnimeGateProbe.request("https://api.animegate.pl/api/v1/auth/discord", "secret=value", "WebView") }
        assertThrows(IllegalArgumentException::class.java) { AnimeGateProbe.request(AnimeGateAuthPolicy.ROOT, "secret=value\r\nInjected: yes", "WebView") }
    }
}
