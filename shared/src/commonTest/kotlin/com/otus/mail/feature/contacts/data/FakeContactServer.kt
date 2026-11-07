package com.otus.mail.feature.contacts.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.ids
import com.otus.mail.testing.resolved
import com.otus.mail.testing.stalwartLike
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A contacts account `c1` in memory with one book, `b1`, that answers `AddressBook/get` and
 * `/changes`, and `ContactCard/query`, `/get`, and `/changes`, with result references. Its card
 * changes name each changed card as updated, or destroyed once it is gone.
 */
internal class FakeContactServer {
    private var cards = emptyMap<String, JsonObject>()
    private var state = 0
    private val log = mutableListOf<Pair<Int, String>>()

    /** Every method called so far. */
    val calls = mutableListOf<String>()

    /** Whether the server has forgotten old states, so `/changes` answers `cannotCalculateChanges`. */
    var forgotStates = false

    /** Whether requests fail as if the network were down. */
    var down = false

    fun put(
        id: String,
        name: String,
    ) {
        cards = cards + (id to card(id, name))
        log += ++state to id
    }

    fun destroy(id: String) {
        cards = cards - id
        log += ++state to id
    }

    fun connections(store: LoginStore) =
        JmapConnections(JmapClient(stalwartLike(CONTACTS_ONLY_SESSION, api = ::answer)), store)

    private fun answer(body: String): String {
        if (down) throw IOException("The network is down")
        val results = mutableMapOf<String, JsonObject>()
        val responses =
            Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                val (name, arguments, id) = call.jsonArray
                val method = name.jsonPrimitive.content
                calls += method
                val result = resolved(arguments.jsonObject, results)?.let { respond(method, it) }
                if (result != null) results[id.jsonPrimitive.content] = result
                val reply = result ?: refusal(method)
                JsonArray(listOf(JsonPrimitive(if (result == null) "error" else method), reply, id))
            }
        return buildJsonObject { put("methodResponses", JsonArray(responses)) }.toString()
    }

    private fun respond(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "AddressBook/get" -> list("b0", listOf(BOOK))
            "AddressBook/changes" -> changes(arguments, "b0", emptyList())
            "ContactCard/query" -> query(arguments)
            "ContactCard/get" -> list("c$state", arguments.ids()?.mapNotNull { cards[it] } ?: cards.values.toList())
            "ContactCard/changes" -> changes(arguments, "c$state", log)
            else -> null
        }

    private fun query(arguments: JsonObject): JsonObject {
        val position = arguments["position"]?.jsonPrimitive?.int ?: 0
        val limit = arguments["limit"]?.jsonPrimitive?.int ?: Int.MAX_VALUE
        val ids =
            cards.keys
                .sorted()
                .drop(position)
                .take(limit)
        return buildJsonObject {
            put("ids", JsonArray(ids.map(::JsonPrimitive)))
            put("position", JsonPrimitive(position))
        }
    }

    private fun changes(
        arguments: JsonObject,
        now: String,
        changed: List<Pair<Int, String>>,
    ): JsonObject? {
        if (forgotStates) return null
        val since =
            arguments
                .getValue("sinceState")
                .jsonPrimitive.content
                .drop(1)
                .toInt()
        val ids = changed.filter { it.first > since }.map { it.second }.distinct()
        return buildJsonObject {
            put("oldState", arguments.getValue("sinceState"))
            put("newState", JsonPrimitive(now))
            put("hasMoreChanges", JsonPrimitive(false))
            put("created", JsonArray(emptyList()))
            put("updated", JsonArray(ids.filter { it in cards }.map(::JsonPrimitive)))
            put("destroyed", JsonArray(ids.filterNot { it in cards }.map(::JsonPrimitive)))
        }
    }

    private fun list(
        state: String,
        list: List<JsonObject>,
    ) = buildJsonObject {
        put("state", JsonPrimitive(state))
        put("list", JsonArray(list))
        put("notFound", JsonArray(emptyList()))
    }

    private fun refusal(method: String) =
        buildJsonObject {
            val type = if (method.endsWith("/changes")) "cannotCalculateChanges" else "invalidResultReference"
            put("type", JsonPrimitive(type))
        }

    private companion object {
        val BOOK =
            Json
                .parseToJsonElement(
                    """{"id": "b1", "name": "Personal", "isDefault": true,
                    "myRights": {"mayRead": true, "mayWrite": true, "mayShare": false, "mayDelete": false}}""",
                ).jsonObject

        fun card(
            id: String,
            name: String,
        ) = Json
            .parseToJsonElement(
                """{"@type": "Card", "version": "1.0", "id": "$id", "addressBookIds": {"b1": true},
                "name": {"full": "$name"}}""",
            ).jsonObject
    }
}
