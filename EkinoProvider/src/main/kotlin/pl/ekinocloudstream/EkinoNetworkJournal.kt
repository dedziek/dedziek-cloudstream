package pl.ekinocloudstream

import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response

/** Passive transport observations only. No URLs, IP addresses, cookies or exception messages. */
internal class EkinoNetworkJournal {
    @Volatile private var last: String? = null
    fun report(): String? = last

    fun factory(): EventListener.Factory = EventListener.Factory { call -> Observation(call) }

    private inner class Observation(call: Call) : EventListener() {
        private val started = System.nanoTime()
        private val events = mutableListOf<String>()
        private var stage = "utworzenie żądania"
        private var http: Int? = null
        private var ray: String? = null
        private val phase = phase(call.request().url.host, call.request().url.encodedPath)

        @Synchronized private fun record(value: String) {
            stage = value
            if (events.size == 14) events.removeAt(1)
            events.add("${elapsed()} ms: $value")
            publish("w toku")
        }

        private fun elapsed(): Long = (System.nanoTime() - started) / 1_000_000L

        @Synchronized private fun publish(result: String) {
            last = "Ekino v12 · raport połączenia\netap=$phase\n" + events.joinToString("\n") +
                "\nwynik=$result; HTTP=${http ?: "brak"}; Ray ID=${ray ?: "brak"}; ostatnie zdarzenie=$stage; czas=${elapsed()} ms"
        }

        override fun callStart(call: Call) { record("start") }
        override fun dnsStart(call: Call, domainName: String) { record("DNS") }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
            record("DNS zakończony: IPv4=${inetAddressList.count { it is Inet4Address }}, IPv6=${inetAddressList.count { it is Inet6Address }}")
        }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            val family = when (inetSocketAddress.address) { is Inet6Address -> "IPv6"; is Inet4Address -> "IPv4"; else -> "inna rodzina" }
            record("łączenie TCP $family")
        }
        override fun secureConnectStart(call: Call) { record("negocjacja TLS") }
        override fun secureConnectEnd(call: Call, handshake: Handshake?) { record("TLS zakończony") }
        override fun connectionAcquired(call: Call, connection: Connection) { record("połączenie gotowe") }
        override fun requestHeadersEnd(call: Call, request: okhttp3.Request) {
            record("wysłano żądanie; User-Agent=${request.headers.values("User-Agent").size}")
        }
        override fun responseHeadersEnd(call: Call, response: Response) {
            http = response.code
            ray = response.header("cf-ray")?.takeIf { it.matches(Regex("[a-fA-F0-9]{16}(?:-[A-Z0-9]{2,8})?")) }
            record("odebrano nagłówki HTTP ${response.code}")
        }
        override fun responseBodyEnd(call: Call, byteCount: Long) { record("zakończono odczyt odpowiedzi") }
        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
            record("nieudane połączenie: ${failure(ioe)}")
        }
        override fun callEnd(call: Call) { publish("odpowiedź odebrana") }
        override fun callFailed(call: Call, ioe: IOException) { publish(failure(ioe)) }
    }

    companion object {
        fun failure(error: IOException): String = when (error) {
            is UnknownHostException -> "błąd DNS"
            is SSLException -> "błąd TLS/certyfikatu"
            is InterruptedIOException -> "limit czasu połączenia"
            else -> "błąd połączenia"
        }

        private fun phase(host: String, path: String): String = when {
            host == "play.ekino.link" -> "strona pośrednia"
            host != "ekino.ws" -> "inne pochodzenie"
            path.startsWith("/search/") -> "wyszukiwanie"
            path.startsWith("/movie/cat/") -> "katalog filmów"
            path == "/serie/" -> "katalog seriali"
            path.startsWith("/watch/f/") -> "ramka odtwarzacza"
            else -> "strona materiału"
        }
    }
}
