package pl.ekinocloudstream

import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class EkinoNetworkJournalTest {
    @Test fun realHttpResponseRecordsThePhaseAndStagesWithoutExportingUrlOrCookies() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("ok").addHeader("cf-ray", "0123456789abcdef-WAW"))
            val journal = EkinoNetworkJournal()
            val base = OkHttpClient.Builder().dns(Dns { listOf(InetAddress.getLoopbackAddress()) }).build()
            val http = EkinoSiteHttp.isolated(base, journal)
            val url = server.url("/search/qf?secret=PRIVATE_QUERY").newBuilder().host("ekino.ws").build()
            val response = http.get(url.toString(), headers = mapOf("User-Agent" to "PRIVATE_AGENT", "Cookie" to "session=PRIVATE_COOKIE"))
            assertEquals("ok", response.text)
            val report = requireNotNull(journal.report())
            assertTrue(report.contains("etap=wyszukiwanie"))
            assertTrue(report.contains("DNS zakończony"))
            assertTrue(report.contains("User-Agent=1"))
            assertTrue(report.contains("HTTP=200"))
            assertTrue(report.contains("Ray ID=0123456789abcdef-WAW"))
            assertFalse(report.contains("PRIVATE"))
            assertFalse(report.contains("127.0.0.1"))
            assertFalse(report.contains("/search/"))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun actualTimeoutStopsQueuedSearchRequestsAfterOneWireAttempt() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("late"))
            val journal = EkinoNetworkJournal()
            val http = EkinoSiteHttp.isolated(OkHttpClient(), journal)
            http.defaultTimeOut = 1
            val gate = EkinoRequestGate()
            val first = async {
                try { gate.request { http.get(server.url("/movie/cat/").toString()).body.close() }; false }
                catch (_: IOException) { true }
            }
            withTimeout(3_000) { while (server.requestCount == 0) yield() }
            val second = async {
                try { gate.request { http.get(server.url("/search/qf").toString()).body.close() }; false }
                catch (_: EkinoRequestGate.Paused) { true }
            }
            assertTrue(first.await())
            assertTrue(second.await())
            assertEquals(1, server.requestCount)
            assertTrue(gate.isPaused)
            val report = requireNotNull(journal.report())
            assertTrue(report.contains("wynik=limit czasu połączenia"))
            assertTrue(report.contains("wysłano żądanie"))
            assertTrue(report.contains("HTTP=brak"))
        } finally { server.shutdown() }
    }

    @Test fun dnsFailureHasItsOwnCauseAndDoesNotRevealTheExceptionMessage() = runBlocking {
        val journal = EkinoNetworkJournal()
        val http = EkinoSiteHttp.isolated(OkHttpClient.Builder().dns(Dns { throw UnknownHostException("PRIVATE_HOST") }).build(), journal)
        try { http.get("https://ekino.ws/movie/cat/"); fail("Expected DNS error") } catch (_: UnknownHostException) { }
        val report = requireNotNull(journal.report())
        assertTrue(report.contains("katalog filmów"))
        assertTrue(report.contains("wynik=błąd DNS"))
        assertFalse(report.contains("PRIVATE"))
    }

    @Test fun tlsStageAndFailureAreReportedWithoutCertificateOrAddressData() {
        val journal = EkinoNetworkJournal()
        val call = OkHttpClient().newCall(Request.Builder().url("https://ekino.ws/movie/show/PRIVATE_TITLE/123").build())
        val listener = journal.factory().create(call)
        listener.callStart(call)
        listener.secureConnectStart(call)
        listener.callFailed(call, SSLHandshakeException("PRIVATE_CERTIFICATE"))
        val report = requireNotNull(journal.report())
        assertTrue(report.contains("wynik=błąd TLS/certyfikatu"))
        assertTrue(report.contains("ostatnie zdarzenie=negocjacja TLS"))
        assertFalse(report.contains("PRIVATE"))
        assertEquals("limit czasu połączenia", EkinoNetworkJournal.failure(InterruptedIOException("PRIVATE_TIMEOUT")))
    }
}
