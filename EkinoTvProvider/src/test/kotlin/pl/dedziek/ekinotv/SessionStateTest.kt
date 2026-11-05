package pl.dedziek.ekinotv

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SessionStateTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences("dedziek_ekino_tv_v1", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun pauseAndPublicRecoveryPageSurviveRestartWithoutSavingTokens() {
        val state = SessionState(context)
        state.remember("https://ekino.ws/movie/show/test/42?token=SECRET#SECRET")
        state.pause("CAPTCHA odtwarzacza", true)
        val restored = SessionState(context)
        assertTrue(restored.paused)
        assertTrue(restored.playerRequired)
        assertEquals("https://ekino.ws/movie/show/test/42", restored.lastMaterial)
        assertFalse(restored.report().contains("SECRET"))
        restored.remember("https://foreign.example/movie/show/test/42")
        assertEquals(state.lastMaterial, restored.lastMaterial)
    }

    @Test fun releaseRequiresAnActualPlayerAndPersistsOnlyAfterSuccess() {
        val state = SessionState(context); state.pause("CAPTCHA", true)
        val page = WebPage("https://ekino.ws/movie/show/test/42", "<h1 class=title>Film</h1>")
        assertFalse(state.accept(BrowserSnapshot(page)))
        assertTrue(SessionState(context).paused)
        val wrapper = WebPage("https://ekino.ws/watch/f/player/id", "<a target=_blank href='https://host.example/e/id'>wideo</a>")
        assertTrue(state.accept(BrowserSnapshot(page, listOf(wrapper))))
        assertFalse(SessionState(context).paused)
    }

    @Test fun navigationBudgetStopsRepeatedOwnDocumentsAndIgnoresChallengeInternals() {
        val budget = NavigationBudget()
        val url = "https://ekino.ws/watch/f/player/abc"
        repeat(3) { assertTrue(budget.record(url, it * 1000L)) }
        repeat(30) { assertTrue(budget.record("https://challenges.cloudflare.com/frame/$it", 3100)) }
        assertFalse(budget.record(url, 4000))
        assertTrue(budget.record(url, 20_000))
        budget.reset(); assertTrue(budget.record(url, 20_001))
    }

    @Test fun slowReloadsAlsoHaveAFiniteBudgetUntilAnExplicitUserRetry() {
        val budget = NavigationBudget()
        val url = "https://ekino.ws/watch/f/player/abc"
        repeat(6) { assertTrue(budget.record(url, it * 20_000L)) }
        assertFalse(budget.record(url, 140_000))
        budget.reset(); assertTrue(budget.record(url, 160_000))
    }
}
