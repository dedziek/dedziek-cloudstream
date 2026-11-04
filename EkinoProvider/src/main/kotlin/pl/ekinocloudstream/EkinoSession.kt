package pl.ekinocloudstream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.webkit.CookieManager
import org.json.JSONObject
import java.io.File
import java.net.HttpCookie
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Session-only storage. No password or session data is included in CloudStream backups. */
class EkinoSession(context: Context) {
    private val storage = AtomicFile(File(context.applicationContext.noBackupFilesDir, "ekino-session-v1.enc"))
    private val lock = Any()
    @Volatile private var snapshot: Snapshot = read()

    private class Snapshot(val cookies: String = "", val agent: String = "")

    fun hasSession(): Boolean = snapshot.cookies.isNotBlank()

    /** Never send Ekino credentials to an external embed/video host. */
    fun cookieHeader(url: String): String = if (isEkinoUrl(url)) snapshot.cookies else ""

    fun userAgent(): String = snapshot.agent

    /** Retain root-scoped cookie rotations without widening a host/path restriction. */
    fun mergeResponseCookies(url: String, cookies: List<String>) {
        if (!isEkinoUrl(url) || cookies.isEmpty()) return
        synchronized(lock) {
            val current = snapshot
            if (current.cookies.isBlank()) return
            val values = linkedMapOf<String, String>()
            current.cookies.split(';').forEach { part ->
                val name = part.substringBefore('=').trim()
                if (name.isNotEmpty() && part.contains('=')) values[name] = part.substringAfter('=').trim()
            }
            // RFC 6265: a missing Path defaults to the directory of the request URL.
            val requestPath = URI(url).path.orEmpty()
            val defaultPath = if (!requestPath.startsWith('/') || requestPath.count { it == '/' } <= 1) "/"
                else requestPath.substringBeforeLast('/').ifEmpty { "/" }
            var touched = false
            for (line in cookies) {
                if (line.contains('\r') || line.contains('\n')) continue
                val parsed = runCatching { HttpCookie.parse(line) }.getOrNull() ?: continue
                for (cookie in parsed) {
                    val domain = cookie.domain?.lowercase()?.removePrefix(".")
                    if (domain != null && domain != "ekino.ws") continue
                    val path = cookie.path?.takeIf { it.isNotEmpty() } ?: defaultPath
                    if (path != "/") continue
                    val name = cookie.name
                    if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) continue
                    if (cookie.maxAge == 0L || cookie.hasExpired()) {
                        if (values.remove(name) != null) touched = true
                    } else if (!cookie.value.contains(';')) {
                        values[name] = cookie.value
                        touched = true
                    }
                }
            }
            if (!touched) return
            val updated = values.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (updated == current.cookies) return
            if (updated.isEmpty()) {
                snapshot = Snapshot()
                storage.delete()
            } else save(updated, current.agent)
        }
    }

    fun save(cookies: String, userAgent: String) {
        require(cookies.isNotBlank() && cookies.length <= 65_536) { "Nieprawidłowa sesja." }
        require(!cookies.contains('\r') && !cookies.contains('\n')) { "Nieprawidłowa sesja." }
        require(userAgent.isNotBlank() && userAgent.length <= 4096) { "Nieprawidłowy identyfikator przeglądarki." }
        require(!userAgent.contains('\r') && !userAgent.contains('\n')) { "Nieprawidłowy identyfikator przeglądarki." }
        synchronized(lock) {
            val data = JSONObject().put("cookies", cookies).put("agent", userAgent).toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(data)
            val bytes = byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + encrypted
            var stream: java.io.FileOutputStream? = null
            try {
                stream = storage.startWrite()
                stream.write(bytes)
                storage.finishWrite(stream)
            } catch (error: Exception) {
                if (stream != null) storage.failWrite(stream)
                throw IllegalStateException("Nie udało się zapisać sesji na urządzeniu.", error)
            }
            snapshot = Snapshot(cookies, userAgent)
        }
    }

    fun clear() {
        val oldCookies = snapshot.cookies
        synchronized(lock) {
            snapshot = Snapshot()
            storage.delete()
        }
        // CookieManager belongs to the host app: only expire our site's cookies.
        val clearCookies = Runnable {
            val manager = CookieManager.getInstance()
            for (origin in ORIGINS) {
                val names = (manager.getCookie(origin).orEmpty() + ";" + oldCookies)
                    .split(';').map { it.substringBefore('=').trim() }
                    .filter { it.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) }.distinct()
                for (name in names) {
                    val expiry = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure"
                    manager.setCookie(origin, expiry)
                    manager.setCookie(origin, "$expiry; Domain=ekino.ws")
                    manager.setCookie(origin, "$expiry; Domain=.ekino.ws")
                }
            }
            manager.flush()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) clearCookies.run()
        else Handler(Looper.getMainLooper()).post(clearCookies)
    }

    private fun read(): Snapshot = synchronized(lock) {
        if (!storage.baseFile.exists()) return@synchronized Snapshot()
        try {
            require(storage.baseFile.length() in 19..100_000)
            val bytes = storage.openRead().use { it.readBytes() }
            require(bytes[0].toInt() == 1)
            val ivLength = bytes[1].toInt() and 0xff
            require(ivLength == 12 && bytes.size > 2 + ivLength + 16)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: return@synchronized Snapshot()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(2, 2 + ivLength)))
            val data = JSONObject(String(cipher.doFinal(bytes.copyOfRange(2 + ivLength, bytes.size)), Charsets.UTF_8))
            val cookies = data.getString("cookies")
            val agent = data.getString("agent")
            require(cookies.length <= 65_536 && agent.length <= 4096)
            require(!cookies.contains('\r') && !cookies.contains('\n') && !agent.contains('\r') && !agent.contains('\n'))
            Snapshot(cookies, agent)
        } catch (_: Exception) {
            // A missing/invalidated device key means a fresh user login is needed.
            Snapshot()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    companion object {
        const val BASE_URL = "https://ekino.ws"
        private const val KEY_ALIAS = "pl.ekinocloudstream.session.v1"
        private val ORIGINS = listOf(BASE_URL)

        fun isEkinoUrl(url: String): Boolean = EkinoLoginPolicy.isEkinoUrl(url)
    }
}
