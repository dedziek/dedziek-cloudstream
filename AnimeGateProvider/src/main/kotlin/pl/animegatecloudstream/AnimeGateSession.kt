package pl.animegatecloudstream

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** A bounded prototype snapshot, not an assertion that authentication has succeeded. */
class AnimeGateSession(context: Context, private val clock: () -> Long = System::currentTimeMillis,
                       private val keyProvider: (() -> SecretKey)? = null) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "animegate-login-probe-v1.enc"))
    private class Snapshot(val url: String, val cookies: String, val agent: String, val saved: Long)
    private val lock = Any()
    @Volatile private var snapshot: Snapshot? = read()

    private fun current(): Snapshot? = snapshot?.takeIf { clock() >= it.saved && clock() - it.saved < MAX_AGE }
    fun hasSession(): Boolean = current() != null
    fun requestUrl(): String? = current()?.url
    fun agent(): String = current()?.agent.orEmpty()
    fun cookieHeader(url: String): String = current()?.takeIf { it.url == url && AnimeGateAuthPolicy.isProbeUrl(url) }?.cookies.orEmpty()

    fun save(url: String, cookies: String, agent: String) {
        validate(url, cookies, agent)
        synchronized(lock) {
            val saved = clock()
            val plain = JSONObject().put("url", url).put("cookies", cookies).put("agent", agent).put("saved", saved).toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Exception) {
                stream?.let(file::failWrite)
                throw IllegalStateException("Nie udało się zapisać sesji na urządzeniu.", error)
            }
            snapshot = Snapshot(url, cookies, agent, saved)
        }
    }

    fun clear() = synchronized(lock) { snapshot = null; file.delete() }

    private fun read(): Snapshot? = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized null
        try {
            require(file.baseFile.length() in 31..100_000)
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes[0].toInt() == 1 && (bytes[1].toInt() and 255) == 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(14, bytes.size)), Charsets.UTF_8))
            val url = json.getString("url"); val cookies = json.getString("cookies"); val agent = json.getString("agent")
            validate(url, cookies, agent)
            Snapshot(url, cookies, agent, json.getLong("saved"))
        } catch (_: Exception) { null }
    }

    private fun key(): SecretKey {
        keyProvider?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private fun validate(url: String, cookies: String, agent: String) {
        require(AnimeGateAuthPolicy.isProbeUrl(url))
        require(cookies.isNotBlank() && cookies.length <= 65_536 && !cookies.contains('\r') && !cookies.contains('\n'))
        require(agent.isNotBlank() && agent.length <= 4096 && !agent.contains('\r') && !agent.contains('\n'))
    }

    companion object {
        const val MAX_AGE = 60 * 60 * 1000L
        private const val KEY_ALIAS = "pl.animegatecloudstream.login-probe.v1"
    }
}
