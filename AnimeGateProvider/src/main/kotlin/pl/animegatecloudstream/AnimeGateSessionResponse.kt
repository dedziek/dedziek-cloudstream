package pl.animegatecloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Keeps only an outcome and a bounded schema of fixed field names/types, never response values. */
internal object AnimeGateSessionResponse {
    data class Result(val outcome: AnimeGateProbe.Outcome, val schema: String)
    private val keys = setOf("data", "user", "session", "result", "payload", "account", "authenticated", "isAuthenticated", "isLoggedIn", "loggedIn", "success", "ok",
        "error", "status", "code", "message", "id", "userId", "discordId", "discord_id", "username", "name", "displayName", "email", "expires", "expiresAt")

    fun inspect(body: String, currentUserEndpoint: Boolean): Result {
        if (body.isBlank()) return Result(AnimeGateProbe.Outcome.UNCONFIRMED, "empty")
        val json = try { ObjectMapper().readTree(body) } catch (_: Exception) { null }
        if (json == null) return Result(AnimeGateProbe.Outcome.UNCONFIRMED,
            when { body.isBlank() -> "empty"; body.trimStart().startsWith("<") -> "html"; else -> "non_json" })
        val schema = shape(json, 0).take(1500)
        if (!json.isObject) return Result(AnimeGateProbe.Outcome.UNCONFIRMED, schema)
        val containers = mutableListOf<JsonNode>()
        fun collect(node: JsonNode, depth: Int) {
            if (!node.isObject || depth > 2) return
            containers.add(node)
            for (key in listOf("data", "session")) node.get(key)?.let { collect(it, depth + 1) }
        }
        collect(json, 0)
        if (containers.any { node -> listOf("authenticated", "isAuthenticated", "isLoggedIn", "loggedIn").any { node.path(it).isBoolean && !node.path(it).asBoolean() } })
            return Result(AnimeGateProbe.Outcome.LOGIN_REQUIRED, schema)
        if (containers.any { node ->
                (node.hasNonNull("error") && !(node.path("error").isBoolean && !node.path("error").asBoolean())) ||
                    listOf("success", "ok").any { node.path(it).isBoolean && !node.path(it).asBoolean() } ||
                    node.path("status").asText().lowercase() in setOf("error", "fail", "failed", "unauthorized", "unauthenticated") ||
                    (node.path("code").isIntegralNumber && node.path("code").asInt() in setOf(401, 403))
            }) return Result(AnimeGateProbe.Outcome.UNCONFIRMED, schema)
        if (json.size() == 0 || containers.any { it.has("user") && it.path("user").isNull })
            return Result(AnimeGateProbe.Outcome.LOGIN_REQUIRED, schema)
        val verifiedIdentity = containers.any { identity(it.path("user")) } || (currentUserEndpoint &&
            listOf(json, json.path("data"), json.path("data").path("data")).any(::identity))
        return Result(when {
            verifiedIdentity -> AnimeGateProbe.Outcome.VERIFIED_SESSION_API
            else -> AnimeGateProbe.Outcome.UNCONFIRMED
        }, schema)
    }

    private fun identity(node: JsonNode): Boolean {
        if (!node.isObject) return false
        val id = listOf("id", "userId", "discordId", "discord_id").any { key ->
            val value = node.path(key)
            (value.isTextual && value.asText().isNotBlank() && value.asText() != "0") || (value.isIntegralNumber && value.asLong() > 0)
        }
        val profile = listOf("username", "name", "displayName", "email").any { node.path(it).isTextual && node.path(it).asText().isNotBlank() }
        return id && profile
    }

    private fun shape(node: JsonNode, depth: Int): String = when {
        node.isObject -> if (depth >= 3) "object" else node.fields().asSequence().take(12).joinToString(",", "{", "}") {
            (if (it.key in keys) it.key else "{field}") + ":" + shape(it.value, depth + 1)
        }
        node.isArray -> "array"
        node.isTextual -> "string"
        node.isNumber -> "number"
        node.isBoolean -> "boolean"
        node.isNull -> "null"
        else -> "other"
    }
}
