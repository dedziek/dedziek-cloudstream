package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateObservedSessionTest {
    @Test fun backendSessionIsSelectedOnlyAfterAnActualGetAndPreferredToTheFrontend() {
        val observed = AnimeGateObservedSession()
        val backend = "https://api.animegate.pl/api/v7/auth/session"
        assertNull(observed.url())
        observed.observe("POST", backend); assertNull(observed.url())
        observed.observe("GET", AnimeGateAuthPolicy.SESSION)
        assertEquals(AnimeGateAuthPolicy.SESSION, observed.url())
        observed.observe("GET", backend)
        assertEquals(backend, observed.url())
        observed.observe("GET", AnimeGateAuthPolicy.SESSION)
        assertEquals(backend, observed.url())
    }
    @Test fun callbackAndQueryUrlsAreNeverSelectedForCredentialProbes() {
        val observed = AnimeGateObservedSession()
        for (url in listOf("https://auth.animegate.pl/api/auth/discord/callback?code=SECRET", "https://api.animegate.pl/api/v7/auth/session?token=SECRET", "https://api.animegate.pl/api/v7/auth/session#SECRET", "https://api.animegate.pl.evil.com/api/v7/auth/session", "http://api.animegate.pl/api/v7/auth/session", "https://api.animegate.pl/api/v7/auth/discord")) observed.observe("GET", url)
        assertNull(observed.url())
    }
    @Test fun validationDoesNotBroadenBackendProbeScopeBeyondTheSessionRoute() {
        assertTrue(AnimeGateAuthPolicy.isSessionProbeUrl("https://api.animegate.pl/api/v7/auth/session"))
        for (url in listOf("https://api.animegate.pl/", "https://api.animegate.pl/api/v7/users/me", "https://auth.animegate.pl/api/v7/auth/session", "https://api.animegate.pl/api/%767/auth/session", "https://api.animegate.pl/api/v7/../auth/session")) assertFalse(url, AnimeGateAuthPolicy.isProbeUrl(url))
    }
}
