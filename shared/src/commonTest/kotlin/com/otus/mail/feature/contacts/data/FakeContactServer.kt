package com.otus.mail.feature.contacts.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.MailCache
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
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
import kotlinx.serialization.json.put

/**
 * One contacts account, `c1`, in memory, for sync tests. It answers `AddressBook/get`,
 * `AddressBook/changes`, `ContactCard/query` from a position, `ContactCard/get`, and
 * `ContactCard/changes`, with result references. Filters, sorts, and properties are ignored.
 */
internal class FakeContactServer {
    private val objects =
        mutableMapOf(
            "AddressBook" to mutableMapOf<String, JsonObject>(),
            "ContactCard" to mutableMapOf(),
        )
    private var state = 0
    private val log = mutableListOf<Change>()

    /** Every method call so far, as its name and arguments. */
    val calls = mutableListOf<Pair<String, JsonObject>>()

    /** Whether the server has forgotten old states, so `/changes` answers `cannotCalculateChanges`. */
    var forgotStates = false

    /** Whether requests fail as if the network were down. */
    var down = false

    fun names(from: Int = 0): List<String> = calls.drop(from).map { it.first }

    /** Saves [json] as a book or card of [type], created or replaced. */
    fun put(
        type: String,
        json: String,
    ) {
        val value = Json.parseToJsonElement(json).jsonObject
        val id = value.getValue("id").jsonPrimitive.content
        val created = id !in objects.getValue(type)
        objects.getValue(type)[id] = value
        log += Change(++state, type, id, if (created) Kind.Created else Kind.Updated)
    }

    fun destroy(
        type: String,
        id: String,
    ) {
        objects.getValue(type).remove(id)
        log += Change(++state, type, id, Kind.Destroyed)
    }

    fun connections(store: LoginStore) =
        JmapConnections(JmapClient(stalwartLike(CONTACTS_ONLY_SESSION, api = ::answer)), store)

    fun repository(
        store: LoginStore,
        cache: MailCache,
    ) = JmapContactRepository(connections(store), store, cache)

    private fun answer(body: String): String {
        if (down) throw IOException("The network is down")
        val results = mutableMapOf<String, JsonObject>()
        val responses =
            Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                val (name, arguments, id) = call.jsonArray
                val method = name.jsonPrimitive.content
                val resolved = resolved(arguments.jsonObject, results)
                resolved?.let { calls += method to it }
                val result = resolved?.let { respond(method, it) }
                result?.let { results[id.jsonPrimitive.content] = it }
                val reply = result ?: buildJsonObject { put("type", error(method, resolved)) }
                JsonArray(listOf(JsonPrimitive(if (result == null) "error" else method), reply, id))
            }
        return buildJsonObject { put("methodResponses", JsonArray(responses)) }.toString()
    }

    private fun error(
        method: String,
        arguments: JsonObject?,
    ) = when {
        arguments == null -> "invalidResultReference"
        method.endsWith("/changes") -> "cannotCalculateChanges"
        else -> "unknownMethod"
    }

    private fun respond(
        method: String,
        arguments: JsonObject,
    ): JsonObject? {
        val (type, verb) = method.split("/")
        val all = objects[type] ?: return null
        return when (verb) {
            "get" -> get(all, arguments)
            "query" -> query(all, arguments)
            "changes" -> changes(type, arguments)
            else -> null
        }
    }

    private fun get(
        all: Map<String, JsonObject>,
        arguments: JsonObject,
    ): JsonObject {
        val ids = (arguments["ids"] as? JsonArray)?.map { it.jsonPrimitive.content }
        return buildJsonObject {
            put("state", "$state")
            put("list", JsonArray(ids?.mapNotNull { all[it] } ?: all.values.toList()))
            put("notFound", JsonArray(ids.orEmpty().filter { it !in all }.map(::JsonPrimitive)))
        }
    }

    private fun query(
        all: Map<String, JsonObject>,
        arguments: JsonObject,
    ): JsonObject {
        val position = arguments["position"]?.jsonPrimitive?.int ?: 0
        val limit = arguments["limit"]?.jsonPrimitive?.int ?: Int.MAX_VALUE
        val ids =
            all.keys
                .sorted()
                .drop(position)
                .take(limit)
        return buildJsonObject {
            put("queryState", "$state")
            put("ids", JsonArray(ids.map(::JsonPrimitive)))
            put("position", position)
        }
    }

    private fun changes(
        type: String,
        arguments: JsonObject,
    ): JsonObject? {
        if (forgotStates) return null
        val since = arguments.getValue("sinceState").jsonPrimitive.int
        val max = arguments["maxChanges"]?.jsonPrimitive?.int ?: Int.MAX_VALUE
        val pending = log.filter { it.state > since && it.type == type }
        val sent = pending.take(max)
        val newState = if (pending.size > max) sent.last().state else state

        fun ids(kind: Kind) = JsonArray(sent.filter { it.kind == kind }.map { JsonPrimitive(it.id) }.distinct())
        return buildJsonObject {
            put("oldState", "$since")
            put("newState", "$newState")
            put("hasMoreChanges", pending.size > max)
            put("created", ids(Kind.Created))
            put("updated", ids(Kind.Updated))
            put("destroyed", ids(Kind.Destroyed))
        }
    }

    /** [arguments] with `#name` result references read from [results]; null when one cannot be read. */
    private fun resolved(
        arguments: JsonObject,
        results: Map<String, JsonObject>,
    ): JsonObject? =
        JsonObject(
            arguments.entries.associate { (key, value) ->
                if (!key.startsWith("#")) return@associate key to value
                val reference = value.jsonObject
                val result = results[reference.getValue("resultOf").jsonPrimitive.content] ?: return null
                key.drop(1) to
                    result.getValue(
                        reference
                            .getValue("path")
                            .jsonPrimitive.content
                            .drop(1),
                    )
            },
        )

    private enum class Kind { Created, Updated, Destroyed }

    private data class Change(
        val state: Int,
        val type: String,
        val id: String,
        val kind: Kind,
    )
}

internal fun book(
    id: String,
    name: String = id,
): String =
    """{"id": "$id", "name": "$name", "isDefault": ${id == "b1"},
       "myRights": {"mayRead": true, "mayWrite": true, "mayShare": false, "mayDelete": false}}"""

internal fun card(
    id: String,
    name: String = id,
    book: String = "b1",
): String =
    """{"@type": "Card", "version": "1.0", "id": "$id", "addressBookIds": {"$book": true}, "name": {"full": "$name"}}"""
