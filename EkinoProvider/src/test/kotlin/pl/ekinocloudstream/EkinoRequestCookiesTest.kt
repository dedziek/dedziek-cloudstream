package pl.ekinocloudstream

import org.junit.Assert.*
import org.junit.Test

class EkinoRequestCookiesTest {
    private val base = EkinoParser.BASE

    @Test fun anonymousDetailResponseKeepsPhpSessionForAllItsPlayerRequests() {
        val cookies = EkinoRequestCookies()
        assertEquals("", cookies.header("$base/movie/show/test/1"))
        cookies.merge("$base/movie/show/test/1", listOf("PHPSESSID=anonymous-fixture; Path=/; HttpOnly"))
        for (host in listOf("player", "dood", "onlys", "upzone")) {
            assertTrue(cookies.header("$base/watch/f/$host/id").contains("PHPSESSID=anonymous-fixture"))
        }
        assertEquals("", cookies.header("https://play.ekino.link/test"))
        assertEquals("", cookies.header("https://host.example/e/test"))
        assertEquals("", EkinoRequestCookies().header(base))
    }

    @Test fun rootAndPathCookiesAreScopedAndRotateWithoutCrossOriginLeakage() {
        val cookies = EkinoRequestCookies("account=saved-fixture")
        cookies.merge("$base/watch/f/player/id", listOf("PHPSESSID=first; Path=/", "gate=watch-only; Path=/watch"))
        assertTrue(cookies.header("$base/watch/f/player/id").contains("gate=watch-only"))
        assertFalse(cookies.header("$base/search/qf").contains("gate="))
        assertTrue(cookies.header("$base/search/qf").contains("account=saved-fixture"))
        cookies.merge("$base/watch/f/player/id", listOf("PHPSESSID=rotated; Path=/"))
        assertFalse(cookies.header(base).contains("PHPSESSID=first"))
        assertTrue(cookies.header(base).contains("PHPSESSID=rotated"))
        cookies.merge(base, listOf("PHPSESSID=deleted; Max-Age=0; Path=/"))
        assertFalse(cookies.header(base).contains("PHPSESSID="))
        for (url in listOf("http://ekino.ws/", "https://ekino.ws.evil.example/", "https://ekino-tv.pl/", "https://ekino.ws:444/")) {
            assertEquals(url, "", cookies.header(url))
        }
    }

    @Test fun foreignCookieDomainsAndHeaderInjectionAreRejected() {
        val cookies = EkinoRequestCookies()
        cookies.merge(base, listOf("foreign=x; Domain=evil.example; Path=/", "parent=x; Domain=ws; Path=/",
            "bad=x\r\nInjected: value; Path=/", "valid=fixture; Path=/; Secure"))
        val header = cookies.header(base)
        assertTrue(header.contains("valid=fixture"))
        assertFalse(header.contains("foreign="))
        assertFalse(header.contains("parent="))
        assertFalse(header.contains("Injected"))
        cookies.merge("https://foreign.example/", listOf("extra=x; Domain=ekino.ws; Path=/"))
        assertFalse(cookies.header(base).contains("extra="))
    }

    @Test fun absentPathUsesRequestDirectoryInsteadOfRoot() {
        val cookies = EkinoRequestCookies()
        cookies.merge("$base/movie/show/test/1", listOf("narrow=fixture"))
        assertTrue(cookies.header("$base/movie/show/test/2").contains("narrow=fixture"))
        assertFalse(cookies.header("$base/watch/f/player/id").contains("narrow="))
    }
}
