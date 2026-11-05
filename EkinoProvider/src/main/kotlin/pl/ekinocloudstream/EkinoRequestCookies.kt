package pl.ekinocloudstream

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI

/** One operation's cookies, including an anonymous PHP session. Never persisted or shared with hosts. */
internal class EkinoRequestCookies(savedRootHeader: String = "") {
    private val manager = CookieManager(null, CookiePolicy { uri, cookie ->
        EkinoParser.isSiteUrl(uri.toString()) && cookie.domain.orEmpty().removePrefix(".").equals("ekino.ws", true)
    })

    init {
        require(savedRootHeader.length <= 65_536 && !savedRootHeader.contains('\r') && !savedRootHeader.contains('\n'))
        val seed = savedRootHeader.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim()
            if (!part.contains('=') || !name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) null
            else "$name=${part.substringAfter('=').trim()}; Path=/; Secure"
        }
        merge("${EkinoParser.BASE}/", seed)
    }

    @Synchronized fun header(url: String): String {
        if (!EkinoParser.isSiteUrl(url)) return ""
        return manager.get(URI(url), emptyMap())["Cookie"].orEmpty().joinToString("; ")
    }

    @Synchronized fun merge(url: String, setCookie: List<String>) {
        if (!EkinoParser.isSiteUrl(url)) return
        val valid = setCookie.filter { it.length <= 16_384 && !it.contains('\r') && !it.contains('\n') }
        manager.put(URI(url), mapOf("Set-Cookie" to valid))
    }
}
