package pl.animegatecloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLDecoder
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** In-memory observations only. Never exports full request URLs, query values or response values. */
internal class AnimeGateCatalogContract {
    class Request internal constructor(internal val url: String, val route: String, val queryKeys: String)
    data class Result(val route: String, val queryKeys: String, val status: Int, val shape: String)
    private val observed = linkedMapOf<String, Request>()
    private val results = mutableListOf<Result>()
    private var state = "NOT_RUN"

    @Synchronized fun observe(method: String, url: String) {
        if (method != "GET" || state == "RUNNING" || !eligible(url)) return
        val uri = URI(url)
        val parts = uri.rawPath.split('/').filter(String::isNotBlank)
        val route = parts.mapIndexed { index, part ->
            when { index == 1 && part.matches(Regex("v[0-9]{1,3}")) -> part
                part in routeWords -> part
                else -> "{param}" }
        }.joinToString("/", prefix = "/")
        val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).map {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            if (key in queryWords) key else "{param}"
        }.distinct().sorted().take(12).joinToString(",").ifEmpty { "NONE" }
        val key = "$route?$query"
        if (observed.size < 24 || observed.containsKey(key)) observed[key] = Request(url, route, query)
    }

    @Synchronized fun requests(): List<Request> = observed.values.take(8)
    @Synchronized fun start() { results.clear(); state = "RUNNING" }
    @Synchronized fun finish(value: String) { require(value in setOf("DONE", "NO_ROUTES", "SESSION_REQUIRED", "ERROR", "CANCELLED", "STOPPED_403", "STOPPED_429")); state = value }
    @Synchronized fun add(request: Request, status: Int, body: String, truncated: Boolean = false): Result {
        require(observed[request.route + "?" + request.queryKeys] === request)
        val result = Result(request.route, request.queryKeys, status,
            if (truncated) "truncated" else schema(body))
        if (results.size < 8) results.add(result)
        return result
    }
    @Synchronized fun report(): String = buildString {
        appendLine("catalog_state=$state")
        appendLine("catalog_observed=${observed.size}")
        appendLine("catalog_tested=${results.size}")
        results.forEachIndexed { i, result ->
            appendLine("catalog_${i + 1}_route=${result.route}")
            appendLine("catalog_${i + 1}_query_keys=${result.queryKeys}")
            appendLine("catalog_${i + 1}_status=${result.status}")
            appendLine("catalog_${i + 1}_shape=${result.shape}")
        }
    }

    fun request(request: Request, cookies: String, agent: String): Result {
        synchronized(this) { require(observed[request.route + "?" + request.queryKeys] === request && eligible(request.url)) }
        require(cookies.length <= 65_536 && !cookies.contains('\r') && !cookies.contains('\n'))
        require(agent.isNotBlank() && agent.length <= 4096 && !agent.contains('\r') && !agent.contains('\n'))
        var connection: HttpsURLConnection? = null
        return try {
            connection = URL(request.url).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            val bytes = ByteArrayOutputStream()
            (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { input ->
                val buffer = ByteArray(8192)
                while (bytes.size() <= 1_048_576) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - bytes.size()))
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                }
            }
            // Only schema is retained, including on errors; no headers or values are exported.
            add(request, status, bytes.toString("UTF-8"), bytes.size() > 1_048_576)
        } catch (_: Exception) { add(request, 0, "") }
        finally { connection?.disconnect() }
    }

    companion object {
        private val routeWords = setOf("api", "anime", "animes", "episodes", "episode", "search", "genres", "genre", "tags", "tag", "catalog", "home", "latest", "recent", "popular", "trending", "featured", "seasonal", "list", "all", "details", "sources", "players", "links", "stream", "videos", "recommendations", "random", "updated", "new", "filters")
        private val queryWords = setOf("q", "query", "search", "title", "page", "limit", "offset", "perPage", "pageSize", "sort", "order", "genre", "genres", "status", "type", "season", "year", "filter", "language")
        private val privateWords = setOf("auth", "me", "user", "users", "profile", "account", "history", "watchlist", "favorite", "favorites", "bookmark", "bookmarks", "settings", "notifications", "progress", "tracking", "subscription", "admin", "add", "delete", "remove", "update", "mark", "follow", "unfollow", "clear", "set", "create", "toggle", "subscribe", "unsubscribe", "rate", "vote", "report", "watch", "play")
        private val fields = routeWords + setOf("data", "result", "results", "items", "meta", "pagination", "success", "message", "code", "error", "id", "animeId", "episodeId", "slug", "title", "titles", "name", "romaji", "english", "polish", "native", "alternativeTitles", "description", "synopsis", "poster", "posterUrl", "poster_url", "image", "imageUrl", "image_url", "cover", "coverUrl", "thumbnail", "thumbnailUrl", "year", "releaseYear", "number", "episodeNumber", "episode_number", "seasonNumber", "total", "totalCount", "count", "perPage", "currentPage", "totalPages", "page", "pageSize", "limit", "offset", "hasNextPage", "nextPage", "studio", "studios", "url", "link", "embed", "embedUrl", "videoUrl", "host", "hostName", "language", "quality", "dubbed", "duration", "rating", "ageRating", "translator", "translators", "createdAt", "updatedAt")

        fun eligible(url: String): Boolean = AnimeGateAuthPolicy.isApi(url) && runCatching {
            val uri = URI(url)
            val parts = uri.rawPath.split('/').filter(String::isNotBlank)
            uri.rawFragment == null && url.length <= 4096 && parts.size in 3..9 && parts[0] == "api" &&
                parts[1].matches(Regex("[A-Za-z0-9_-]{1,32}")) && parts[2] in setOf("anime", "animes", "episodes", "search", "genres", "tags", "catalog", "home") &&
                parts.none { it.lowercase(Locale.ROOT) in privateWords || it == "." || it == ".." || it.contains('%') } &&
                uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).all {
                    val key = URLDecoder.decode(it.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
                    key !in setOf("token", "access_token", "code", "state", "authorization", "password", "email", "api_key", "apikey", "auth", "cookie", "session", "sid", "jwt", "signature", "sig")
                }
        }.getOrDefault(false)

        private fun schema(body: String): String {
            if (body.isBlank()) return "empty"
            val json = try { ObjectMapper().readTree(body) } catch (_: Exception) { null }
                ?: return if (body.trimStart().startsWith("<")) "html" else "non_json"
            var remaining = 256
            fun shape(node: JsonNode, depth: Int): String {
                if (--remaining < 0) return "bounded"
                return when {
                depth >= 6 -> when { node.isObject -> "object"; node.isArray -> "array"; else -> "value" }
                node.isObject -> node.fields().asSequence().take(24).joinToString(",", "{", "}") {
                    (if (it.key in fields) it.key else "{field}") + ":" + shape(it.value, depth + 1)
                }
                node.isArray -> if (node.size() == 0) "array[]" else "array[" + shape(node[0], depth + 1) + "]"
                node.isTextual -> "string"
                node.isNumber -> "number"
                node.isBoolean -> "boolean"
                node.isNull -> "null"
                else -> "other"
                }
            }
            return shape(json, 0).take(3500)
        }
    }
}
