package com.otus.mail.feature.inbox.data

import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A mail account in memory that answers JMAP requests as [answer], for list tests. It knows
 * `Mailbox/get`, `Email/query` by mailbox, `Email/get`, `Thread/get`, `Email/changes`,
 * `Thread/changes`, and `Email/set` updates, with result references. Other filter conditions are
 * ignored, so tests check them in [calls]. Threads hold their emails oldest first.
 */
internal class FakeMailServer(
    mailboxes: String,
    emails: String = "",
) {
    private val mailboxes = Json.parseToJsonElement("[$mailboxes]").jsonArray
    private var emails =
        Json
            .parseToJsonElement("[$emails]")
            .jsonArray
            .map { it.jsonObject }
            .associateBy { it.id }
    private var state = 0
    private val log = mutableListOf<Change>()

    /** Every request body so far. */
    val bodies = mutableListOf<String>()

    /** Every method call so far, as its name and arguments. */
    val calls = mutableListOf<Pair<String, JsonObject>>()

    /** Methods that answer with an error, such as `Thread/get`, of [refusalType] when it is set. */
    var refused: Set<String> = emptySet()
    var refusalType: String? = null

    /** Whether the server has forgotten old states, so `/changes` cannot be calculated. */
    var forgotStates = false

    /** Whether requests fail as if the network were down. */
    var down = false

    /** Emails whose `Email/set` updates the server refuses. */
    var refusedUpdates: Set<String> = emptySet()

    /** Whether `Email/query` offers `Email/queryChanges` for its results. */
    var followsQueries = false

    // Each query state's results, by filter and sort, for `Email/queryChanges`.
    private val queries = mutableMapOf<String, List<String>>()

    fun calls(name: String): List<JsonObject> = calls.filter { it.first == name }.map { it.second }

    /** The ids each `Email/get` with explicit ids asked for. */
    fun fetchedIds(): List<List<String>> =
        calls("Email/get").mapNotNull { it["ids"] as? JsonArray }.map { it.strings() }

    fun add(email: String) {
        val added = Json.parseToJsonElement(email).jsonObject
        emails = emails + (added.id to added)
        changed(setOf(added.id), threads = setOf(added.threadId))
    }

    fun destroy(id: String) {
        val thread = emails.getValue(id).threadId
        emails = emails - id
        log += Change(++state, emptySet(), setOf(id), setOf(thread))
    }

    fun update(
        id: String,
        change: (JsonObject) -> JsonObject,
    ) {
        emails = emails + (id to change(emails.getValue(id)))
        changed(setOf(id), threads = emptySet())
    }

    fun answer(body: String): String {
        if (down) throw IOException("The network is down")
        bodies += body
        val results = mutableMapOf<String, JsonObject>()
        val responses =
            Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                val (name, arguments, id) = call.jsonArray
                val method = name.jsonPrimitive.content
                val resolved = resolved(arguments.jsonObject, results)
                if (resolved != null) calls += method to resolved
                val result = if (resolved == null || method in refused) null else respond(method, resolved)
                if (result != null) results[id.jsonPrimitive.content] = result
                JsonArray(listOf(JsonPrimitive(if (result == null) "error" else method), result ?: refusal(method), id))
            }
        return buildJsonObject { put("methodResponses", JsonArray(responses)) }.toString()
    }

    private fun refusal(method: String): JsonObject =
        buildJsonObject {
            val type = if (method.endsWith("/changes")) "cannotCalculateChanges" else "requestTooLarge"
            put("type", JsonPrimitive(refusalType ?: type))
        }

    private fun respond(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Mailbox/get" -> listResult("m", mailboxes.filter { arguments.ids()?.contains(it.id) ?: true })
            "Mailbox/changes" -> mailboxChanges(arguments)
            "Email/queryChanges" -> queryChanges(arguments)
            "Email/get" -> emailGet(arguments)
            "Thread/get" -> listResult("t$state", threadsOf(arguments.ids() ?: emptyList()))
            "Email/query" -> query(arguments)
            "Email/changes" -> changes(arguments, "e") { it.emails }
            "Thread/changes" -> changes(arguments, "t") { it.threads }
            "Email/set" -> set(arguments)
            else -> null
        }

    private fun emailGet(arguments: JsonObject): JsonObject {
        val properties = (arguments["properties"] as? JsonArray)?.strings()?.toSet()
        return listResult(
            "e$state",
            (arguments.ids() ?: emails.keys.toList())
                .mapNotNull { emails[it] }
                .map { email -> email.only(properties) },
        )
    }

    private fun threadsOf(ids: List<String>): List<JsonObject> =
        ids.distinct().mapNotNull { thread ->
            emails.values
                .filter { it.threadId == thread }
                .sortedBy { it.receivedAt }
                .takeIf { it.isNotEmpty() }
                ?.let { members ->
                    buildJsonObject {
                        put("id", JsonPrimitive(thread))
                        put("emailIds", JsonArray(members.map { JsonPrimitive(it.id) }))
                    }
                }
        }

    private fun query(arguments: JsonObject): JsonObject {
        val rows = rows(arguments)
        val position = arguments["position"]?.jsonPrimitive?.int ?: 0
        val limit = arguments["limit"]?.jsonPrimitive?.int ?: rows.size
        queries["${arguments.listKey}@$state"] = rows
        return buildJsonObject {
            put("queryState", JsonPrimitive("q$state"))
            put("ids", JsonArray(rows.drop(position).take(limit).map { JsonPrimitive(it) }))
            put("position", JsonPrimitive(position))
            put("total", JsonPrimitive(rows.size))
            put("canCalculateChanges", JsonPrimitive(followsQueries))
        }
    }

    /** The changes since a query state this server answered; ids that kept their place stay out. */
    private fun queryChanges(arguments: JsonObject): JsonObject? {
        val since =
            arguments
                .getValue("sinceQueryState")
                .jsonPrimitive.content
                .removePrefix("q")
        val old = queries["${arguments.listKey}@$since"] ?: return null
        val rows = rows(arguments)
        queries["${arguments.listKey}@$state"] = rows
        return buildJsonObject {
            put("oldQueryState", JsonPrimitive("q$since"))
            put("newQueryState", JsonPrimitive("q$state"))
            put("total", JsonPrimitive(rows.size))
            put("removed", JsonArray((old - rows.toSet()).map(::JsonPrimitive)))
            val added =
                rows.withIndex().filter { it.value !in old }.map {
                    buildJsonObject {
                        put("id", JsonPrimitive(it.value))
                        put("index", JsonPrimitive(it.index))
                    }
                }
            put("added", JsonArray(added))
        }
    }

    /** Mailboxes never change here, so every state is the current one. */
    private fun mailboxChanges(arguments: JsonObject) =
        buildJsonObject {
            put("oldState", arguments.getValue("sinceState"))
            put("newState", JsonPrimitive("m"))
            put("hasMoreChanges", JsonPrimitive(false))
            put("created", JsonArray(emptyList()))
            put("updated", JsonArray(emptyList()))
            put("destroyed", JsonArray(emptyList()))
        }

    private fun rows(arguments: JsonObject): List<String> {
        val boxes = mailboxesIn(arguments.getValue("filter").jsonObject)
        val ascending =
            arguments["sort"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("isAscending")
                ?.jsonPrimitive
                ?.boolean
        val inBoxes = emails.values.filter { email -> email.boxes.any { it in boxes } }.sortedBy { it.receivedAt }
        val ordered = if (ascending == true) inBoxes else inBoxes.reversed()
        val collapsed = arguments["collapseThreads"] == JsonPrimitive(true)
        return (if (collapsed) ordered.distinctBy { it.threadId } else ordered).map { it.id }
    }

    private fun changes(
        arguments: JsonObject,
        prefix: String,
        ids: (Change) -> Set<String>,
    ): JsonObject? {
        val since =
            arguments
                .getValue("sinceState")
                .jsonPrimitive.content
                .removePrefix(prefix)
                .toIntOrNull()
        if (forgotStates || since == null || since > state) return null
        val later = log.filter { it.state > since }
        val destroyed = if (prefix == "e") later.flatMap { it.destroyed }.toSet() else emptySet()
        return buildJsonObject {
            put("oldState", JsonPrimitive("$prefix$since"))
            put("newState", JsonPrimitive("$prefix$state"))
            put("hasMoreChanges", JsonPrimitive(false))
            put("updated", JsonArray((later.flatMap(ids).toSet() - destroyed).map(::JsonPrimitive)))
            put("destroyed", JsonArray(destroyed.map(::JsonPrimitive)))
        }
    }

    private fun set(arguments: JsonObject): JsonObject {
        val (refusals, updates) =
            arguments
                .getValue("update")
                .jsonObject.entries
                .partition { it.key in refusedUpdates }
        updates.forEach { (id, patch) ->
            update(id) { email ->
                patch.jsonObject.entries.fold(email) { patched, (path, value) -> patched.at(path, value) }
            }
        }
        return buildJsonObject {
            put("newState", JsonPrimitive("e$state"))
            put("updated", JsonObject(updates.associate { it.key to JsonNull }))
            put(
                "notUpdated",
                JsonObject(
                    refusals.associate {
                        it.key to
                            buildJsonObject { put("type", JsonPrimitive("forbidden")) }
                    },
                ),
            )
        }
    }

    private fun changed(
        ids: Set<String>,
        threads: Set<String>,
    ) {
        log += Change(++state, ids, emptySet(), threads)
    }

    private fun listResult(
        state: String,
        list: List<JsonElement>,
    ) = buildJsonObject {
        put("state", JsonPrimitive(state))
        put("list", JsonArray(list))
    }

    private class Change(
        val state: Int,
        val emails: Set<String>,
        val destroyed: Set<String>,
        val threads: Set<String>,
    )
}
