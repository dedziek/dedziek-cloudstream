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
    class Request internal constructor(internal val url: String, val route: String, val queryKeys: String) {
        val origin: String = originOf(url)
        val key: String get() = "$origin|$route?$queryKeys"
    }
    data class Result(val route: String, val queryKeys: String, val status: Int, val shape: String, val origin: String,
                      val parsedEpisodes: Int = -1, val parsedAnime: Int = -1, val parsedDetails: Int = -1)
    private val observed = linkedMapOf<String, Request>()
    private val results = mutableListOf<Result>()
    private var state = "NOT_RUN"

    @Synchronized fun observe(method: String, url: String) {
        if (method != "GET" || state == "RUNNING" || !eligible(url)) return
        val uri = URI(url)
        val parts = uri.rawPath.split('/').filter(String::isNotBlank)
        val route = parts.mapIndexed { index, part ->
            when { index == 1 && part.matches(Regex("v[0-9]{1,3}")) -> part
                part in routeWords || (index == namespaceIndex(url) && publicNamespace(part)) -> part
                else -> "{param}" }
        }.joinToString("/", prefix = "/")
        val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).map {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            if (key in queryWords || propertyName(key)) key else "{param}"
        }.distinct().sorted().take(12).joinToString(",").ifEmpty { "NONE" }
        val request = Request(url, route, query)
        if (observed.size < 24 || observed.containsKey(request.key)) observed[request.key] = request
    }

    @Synchronized fun requests(): List<Request> = observed.values.take(8)
    @Synchronized fun start() { results.clear(); state = "RUNNING" }
    @Synchronized fun finish(value: String) { require(value in setOf("DONE", "NO_ROUTES", "SESSION_REQUIRED", "ERROR", "CANCELLED", "STOPPED_403", "STOPPED_429")); state = value }
    @Synchronized fun add(request: Request, status: Int, body: String, truncated: Boolean = false): Result {
        require(observed[request.key] === request)
        val episodeRoute = AnimeGateAuthPolicy.isApi(request.url) && URI(request.url).rawPath.matches(Regex("/api/[A-Za-z0-9_-]{1,32}/episodes/anime/[^/]+"))
        val episodes = if (status == 200 && !truncated && episodeRoute) runCatching { AnimeGateEpisodeParser.parse(body).size }.getOrDefault(-1) else -1
        val path = URI(request.url).rawPath
        val catalogRoute = AnimeGateAuthPolicy.isApi(request.url) && path == "/api/v1/anime/catalog-search"
        val detailRoute = AnimeGateAuthPolicy.isApi(request.url) && path.matches(Regex("/api/v1/anime/slug/[^/]+"))
        val anime = if (status == 200 && !truncated && catalogRoute) runCatching { AnimeGateCatalogParser.parsePage(body).anime.size }.getOrDefault(-1) else -1
        val details = if (status == 200 && !truncated && detailRoute) runCatching {
            AnimeGateCatalogParser.parseDetails(body, path.substringAfterLast('/')); 1
        }.getOrDefault(-1) else -1
        val result = Result(request.route, request.queryKeys, status,
            if (truncated) "truncated" else schema(body), request.origin, episodes, anime, details)
        if (results.size < 8) results.add(result)
        return result
    }
    @Synchronized fun report(): String = buildString {
        appendLine("catalog_state=$state")
        appendLine("catalog_observed=${observed.size}")
        appendLine("catalog_tested=${results.size}")
        results.forEachIndexed { i, result ->
            appendLine("catalog_${i + 1}_route=${result.route}")
            appendLine("catalog_${i + 1}_origin=${result.origin}")
            appendLine("catalog_${i + 1}_query_keys=${result.queryKeys}")
            appendLine("catalog_${i + 1}_status=${result.status}")
            appendLine("catalog_${i + 1}_shape=${result.shape}")
            if (result.parsedEpisodes >= 0) appendLine("catalog_${i + 1}_parsed_episodes=${result.parsedEpisodes}")
            if (result.parsedAnime >= 0) appendLine("catalog_${i + 1}_parsed_anime=${result.parsedAnime}")
            if (result.parsedDetails >= 0) appendLine("catalog_${i + 1}_parsed_details=${result.parsedDetails}")
        }
    }

    fun request(request: Request, cookies: String, agent: String): Result {
        synchronized(this) { require(observed[request.key] === request && eligible(request.url)) }
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
        private fun originOf(url: String): String = when { AnimeGateAuthPolicy.isApi(url) -> "API"; AnimeGateAuthPolicy.isSite(url) -> "SITE"; else -> "NONE" }
        private fun namespaceIndex(url: String): Int = if (AnimeGateAuthPolicy.isApi(url) ||
            URI(url).rawPath.split('/').filter(String::isNotBlank).getOrNull(1)?.matches(Regex("v[0-9]{1,3}")) == true) 2 else 1
        private fun publicNamespace(value: String): Boolean = value.matches(Regex("[a-z][a-z-]{1,23}")) &&
            privateWords.none { word -> value == word || value.startsWith("$word-") } &&
            value !in setOf("csrf", "session", "token", "oauth", "login", "logout", "signin", "signout", "discord", "revalidate", "invalidate", "refresh", "health", "ping", "config")
        private fun propertyName(value: String): Boolean = value.matches(Regex("[a-z][a-zA-Z_]{1,39}")) &&
            value.split(Regex("(?<=[a-z])(?=[A-Z])|_")).all { it.lowercase(Locale.ROOT) in propertyWords }
        private val propertyWords = setOf("include", "with", "group", "by", "source", "sources", "link", "links", "anime", "episode", "episodes", "host", "hoster", "provider", "website", "url", "name", "display", "title", "original", "japanese", "english", "polish", "romaji", "date", "air", "release", "count", "total", "number", "season", "index", "order", "sort", "priority", "position", "duration", "seconds", "minutes", "filler", "special", "is", "has", "available", "enabled", "premium", "quality", "translation", "translated", "translator", "language", "subtitle", "subtitles", "page", "size", "limit", "offset", "query", "search", "filter", "type", "status", "genre", "genres", "tag", "tags", "image", "poster", "cover", "thumbnail", "details", "data", "id")
        private val routeWords = setOf("api", "anime", "animes", "episodes", "episode", "search", "genres", "genre", "tags", "tag", "catalog", "home", "latest", "recent", "popular", "trending", "featured", "seasonal", "list", "all", "details", "sources", "players", "links", "stream", "videos", "recommendations", "random", "updated", "new", "filters", "catalog-search", "search-suggestions", "slug")
        private val queryWords = setOf("q", "query", "search", "title", "page", "limit", "offset", "perPage", "pageSize", "sort", "order", "genre", "genres", "status", "type", "season", "year", "filter", "language", "mode", "publicOnly")
        private val privateWords = setOf("auth", "me", "my", "user", "users", "profile", "account", "history", "watchlist", "favorite", "favorites", "bookmark", "bookmarks", "settings", "notifications", "progress", "tracking", "subscription", "subscriptions", "library", "collections", "personal", "activity", "preferences", "permissions", "roles", "members", "friends", "admin", "add", "delete", "remove", "update", "mark", "follow", "unfollow", "clear", "set", "create", "toggle", "subscribe", "unsubscribe", "rate", "vote", "report", "watch", "play", "csrf", "session", "token", "oauth", "login", "logout", "signin", "signout", "discord", "callback", "authorize", "register", "credentials", "password", "verify", "revalidate", "invalidate", "refresh", "graphql", "trpc", "rpc")
        private val fields = routeWords + setOf("coverImage", "bannerImage", "effectiveRating", "episodeCount", "mode", "hasPrevPage") + setOf("data", "result", "results", "items", "meta", "pagination", "success", "message", "code", "error", "id", "animeId", "episodeId", "slug", "title", "titles", "name", "romaji", "english", "polish", "native", "alternativeTitles", "description", "synopsis", "poster", "posterUrl", "poster_url", "image", "imageUrl", "image_url", "cover", "coverUrl", "thumbnail", "thumbnailUrl", "year", "releaseYear", "number", "episodeNumber", "episode_number", "seasonNumber", "total", "totalCount", "count", "perPage", "currentPage", "totalPages", "page", "pageSize", "limit", "offset", "hasNextPage", "nextPage", "studio", "studios", "url", "link", "embed", "embedUrl", "videoUrl", "host", "hostName", "language", "quality", "dubbed", "duration", "rating", "ageRating", "translator", "translators", "createdAt", "updatedAt")

        fun eligible(url: String): Boolean = (AnimeGateAuthPolicy.isApi(url) || AnimeGateAuthPolicy.isSite(url)) && runCatching {
            val uri = URI(url)
            val parts = uri.rawPath.split('/').filter(String::isNotBlank)
            val namespace = namespaceIndex(url)
            uri.rawFragment == null && url.length <= 4096 && parts.size in (namespace + 1)..9 && parts[0] == "api" &&
                (namespace != 2 || parts[1].matches(Regex("[A-Za-z0-9_-]{1,32}"))) && publicNamespace(parts[namespace]) &&
                parts.none { it.lowercase(Locale.ROOT) in privateWords || it == "." || it == ".." || it.contains('%') } &&
                uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).all {
                    val key = URLDecoder.decode(it.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
                    key !in setOf("token", "access_token", "code", "state", "authorization", "password", "email", "api_key", "apikey", "auth", "cookie", "session", "sid", "jwt", "signature", "sig", "secret", "client_secret", "key", "nonce")
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
                    val field = if (it.key in fields || (it.value.isValueNode && propertyName(it.key))) it.key else "{field}"
                    val privateContainer = it.key.lowercase(Locale.ROOT) in setOf("user", "users", "profile", "account", "headers", "cookies", "authorization", "history", "watchlist", "translators", "authors", "staff", "contributors")
                    field + ":" + when {
                        privateContainer && it.value.isContainerNode -> "redacted"
                        it.key !in fields && it.value.isObject -> "object"
                        else -> shape(it.value, depth + 1)
                    }
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
