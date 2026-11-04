package pl.animegatecloudstream

import android.content.Context
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnimeGateSessionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val key = SecretKeySpec(ByteArray(16) { 7 }, "AES")
    @Test fun backendSnapshotNeverForwardsItsCookiesToFrontendAuthOrOtherVersions() {
        val backend = "https://api.animegate.pl/api/v7/auth/session"
        AnimeGateSession(context, { 1000 }, { key }).save(backend, "api_session=PRIVATE", "Real WebView")
        val restored = AnimeGateSession(context, { 1001 }, { key })
        assertEquals("api_session=PRIVATE", restored.cookieHeader(backend))
        for (url in listOf(AnimeGateAuthPolicy.ROOT, AnimeGateAuthPolicy.SESSION, "https://auth.animegate.pl/api/auth/discord/callback", "https://api.animegate.pl/api/v8/auth/session", "https://discord.com/")) assertEquals("", restored.cookieHeader(url))
        assertThrows(IllegalArgumentException::class.java) { restored.save("https://auth.animegate.pl/api/auth/session", "private=value", "WebView") }
    }
    @Test fun encryptedSnapshotSurvivesRecreationAndHasAnExactUrlScope() {
        val session = AnimeGateSession(context, { 1000 }, { key })
        session.save(AnimeGateAuthPolicy.SESSION, "private_session=SECRET", "Real WebView")
        val bytes = File(context.noBackupFilesDir, "animegate-login-probe-v1.enc").readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SECRET"))
        val restored = AnimeGateSession(context, { 1001 }, { key })
        assertTrue(restored.hasSession())
        assertEquals("private_session=SECRET", restored.cookieHeader(AnimeGateAuthPolicy.SESSION))
        assertEquals("", restored.cookieHeader(AnimeGateAuthPolicy.ROOT))
        assertEquals("", restored.cookieHeader("https://discord.com/"))
        assertEquals("", restored.cookieHeader("https://api.animegate.pl/api/v1/auth/discord"))
    }
    @Test fun expiryAndClockRollbackInvalidateThePrototypeSnapshot() {
        var now = 1000L
        val session = AnimeGateSession(context, { now }, { key })
        session.save(AnimeGateAuthPolicy.ROOT, "session=private", "WebView")
        now += AnimeGateSession.MAX_AGE
        assertFalse(session.hasSession())
        now = 999
        assertFalse(session.hasSession())
    }
    @Test fun wrongKeyAndTamperingDoNotExposeOrAcceptTheSnapshot() {
        AnimeGateSession(context, { 1000 }, { key }).save(AnimeGateAuthPolicy.ROOT, "session=private", "WebView")
        val wrong = SecretKeySpec(ByteArray(16) { 9 }, "AES")
        assertFalse(AnimeGateSession(context, { 1001 }, { wrong }).hasSession())
        val file = File(context.noBackupFilesDir, "animegate-login-probe-v1.enc")
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertFalse(AnimeGateSession(context, { 1001 }, { key }).hasSession())
    }
    @Test fun clearDoesNotDeleteAnotherPluginsStorage() {
        val other = File(context.noBackupFilesDir, "filman-session-v1.enc").apply { writeText("untouched") }
        val session = AnimeGateSession(context, { 1000 }, { key })
        session.save(AnimeGateAuthPolicy.ROOT, "session=private", "WebView"); session.clear()
        assertFalse(session.hasSession()); assertEquals("untouched", other.readText())
    }
}
