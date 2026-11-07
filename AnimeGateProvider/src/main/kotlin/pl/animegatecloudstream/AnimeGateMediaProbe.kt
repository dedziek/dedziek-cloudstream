package pl.animegatecloudstream

import java.net.URI

internal class AnimeGateMediaException(message: String) : Exception(message)

/** Inspect a bounded response prefix, not a filename: Playmogo can return extensionless HLS. */
internal class AnimeGateMediaProbe(
    private val fetch: suspend (String, Map<String, String>) -> Response,
    private val userAgent: String = ""
) {
    enum class Format { VIDEO, HLS, DASH }
    data class Response(val status: Int, val contentType: String, val prefix: ByteArray, val location: String? = null)

    suspend fun inspect(url: String, referer: String, headers: Map<String, String>): Format {
        var target = url
        var requestHeaders = headers.filterKeys { !it.equals("Range", true) }.toMutableMap()
        if (referer.isNotBlank() && requestHeaders.keys.none { it.equals("Referer", true) }) requestHeaders["Referer"] = referer
        if (userAgent.isNotBlank() && requestHeaders.keys.none { it.equals("User-Agent", true) }) requestHeaders["User-Agent"] = userAgent
        requestHeaders["Range"] = "bytes=0-8191"
        var retriedWithoutRange = false
        val visited = mutableSetOf<String>()
        repeat(5) {
            if (!external(target)) throw AnimeGateMediaException("Playmogo zwróciło nieprawidłowy adres wideo")
            if (!visited.add(target)) throw AnimeGateMediaException("Hosting zapętla przekierowanie wideo")
            val response = fetch(target, requestHeaders)
            if (response.status == 416 && !retriedWithoutRange) {
                // Some valid streams do not accept Range. The fallback is still a bounded read.
                retriedWithoutRange = true
                requestHeaders = requestHeaders.filterKeys { !it.equals("Range", true) }.toMutableMap()
                visited.remove(target)
                return@repeat
            }
            if (response.status in 300..399) {
                val next = runCatching { URI(target).resolve(response.location.orEmpty()).toString() }.getOrNull()
                    ?.takeIf { response.location?.isNotBlank() == true && external(it) }
                    ?: throw AnimeGateMediaException("Hosting zwrócił nieprawidłowe przekierowanie")
                if (URI(next).host != URI(target).host) requestHeaders = requestHeaders.filterKeys {
                    !it.equals("Cookie", true) && !it.equals("Authorization", true)
                }.toMutableMap()
                target = next
                return@repeat
            }
            if (response.status !in setOf(200, 206)) throw AnimeGateMediaException("Hosting wideo zwrócił HTTP ${response.status}")
            return format(response)
        }
        throw AnimeGateMediaException("Hosting zwrócił zbyt wiele przekierowań")
    }

    companion object {
        fun external(url: String): Boolean = runCatching {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) &&
                host.contains('.') && !host.matches(Regex("[0-9.]+")) && ':' !in host &&
                listOf(".localhost", ".local", ".internal", ".lan", ".home").none(host::endsWith) &&
                host != "animegate.pl" && !host.endsWith(".animegate.pl")
        }.getOrDefault(false)

        fun format(response: Response): Format {
            val bytes = response.prefix
            if (bytes.isEmpty()) throw AnimeGateMediaException("Hosting zwrócił pustą odpowiedź zamiast wideo")
            val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (Regex("^#EXTM3U(?:\\r?\\n|$)").containsMatchIn(text)) return Format.HLS
            if (Regex("^(?:<\\?xml[^>]*>\\s*)?<MPD(?:\\s|>)").containsMatchIn(text)) return Format.DASH
            val mime = response.contentType.substringBefore(';').trim().lowercase()
            if (mime == "text/html" || Regex("(?is)^<(?:!doctype\\s+html|html|head|body|script)(?:\\s|>)").containsMatchIn(text))
                throw AnimeGateMediaException("Hosting zwrócił stronę HTML zamiast wideo")
            if (mime == "application/json" || text.startsWith('{') || text.startsWith('['))
                throw AnimeGateMediaException("Hosting zwrócił odpowiedź JSON zamiast wideo")
            fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 255) == values[it] }
            val mp4 = bytes.size >= 8 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) in setOf("ftyp", "styp", "moov", "moof", "mdat")
            val matroska = starts(0x1a, 0x45, 0xdf, 0xa3)
            val ts = bytes.size > 376 && listOf(0, 188, 376).all { bytes[it] == 0x47.toByte() }
            val flv = starts(0x46, 0x4c, 0x56, 0x01)
            val ps = starts(0x00, 0x00, 0x01, 0xba)
            val avi = bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "AVI "
            if (mp4 || matroska || ts || flv || ps || avi) return Format.VIDEO
            throw AnimeGateMediaException("Nie rozpoznano obsługiwanego kontenera wideo z hostingu")
        }
    }
}
