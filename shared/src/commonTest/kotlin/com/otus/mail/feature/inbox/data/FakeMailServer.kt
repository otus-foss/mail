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

    /** Methods that answer with an error, such as `Thread/get`. */
    var refused: Set<String> = emptySet()

    /** Whether the server has forgotten old states, so `/changes` cannot be calculated. */
    var forgotStates = false

    /** Whether requests fail as if the network were down. */
    var down = false

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
            put("type", JsonPrimitive(if (method.endsWith("/changes")) "cannotCalculateChanges" else "requestTooLarge"))
        }

    private fun respond(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Mailbox/get" -> listResult("m", mailboxes.toList())
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
        val rows = if (collapsed) ordered.distinctBy { it.threadId } else ordered
        val position = arguments["position"]?.jsonPrimitive?.int ?: 0
        val limit = arguments["limit"]?.jsonPrimitive?.int ?: rows.size
        return buildJsonObject {
            put("queryState", JsonPrimitive("q$state"))
            put("ids", JsonArray(rows.drop(position).take(limit).map { JsonPrimitive(it.id) }))
            put("position", JsonPrimitive(position))
            put("total", JsonPrimitive(rows.size))
        }
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
        val updates = arguments.getValue("update").jsonObject
        updates.forEach { (id, patch) ->
            update(id) { email ->
                patch.jsonObject.entries.fold(email) { patched, (path, value) -> patched.at(path, value) }
            }
        }
        return buildJsonObject {
            put("newState", JsonPrimitive("e$state"))
            put("updated", JsonObject(updates.mapValues { JsonNull }))
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

/**
 * Arguments with `#ids` replaced by the earlier result it refers to (RFC 8620, section 3.7); null
 * when that call failed, which the server answers with an error.
 */
private fun resolved(
    arguments: JsonObject,
    results: Map<String, JsonObject>,
): JsonObject? {
    val reference = arguments["#ids"]?.jsonObject ?: return arguments
    val path =
        reference
            .getValue("path")
            .jsonPrimitive.content
            .trim('/')
            .split('/')
    return results[reference.getValue("resultOf").jsonPrimitive.content]?.let { source ->
        val ids =
            path
                .fold(listOf<JsonElement>(source)) { elements, step ->
                    elements.flatMap {
                        if (step == "*") it.jsonArray.toList() else listOfNotNull((it as? JsonObject)?.get(step))
                    }
                }.flatMap { if (it is JsonArray) it.toList() else listOf(it) }
        JsonObject(arguments - "#ids" + ("ids" to JsonArray(ids)))
    }
}

/** The mailboxes a list filter names: an AND's first condition holds them, and an OR lists them. */
private fun mailboxesIn(filter: JsonObject): Set<String> {
    val conditions = filter["conditions"]?.jsonArray.orEmpty().map { it.jsonObject }
    return when {
        "inMailbox" in filter -> setOf(filter.getValue("inMailbox").jsonPrimitive.content)
        filter["operator"] == JsonPrimitive("AND") -> mailboxesIn(conditions.first())
        else -> conditions.flatMap(::mailboxesIn).toSet()
    }
}

/** [path] is a property, which [value] replaces, or `property/key`, which it sets or clears. */
private fun JsonObject.at(
    path: String,
    value: JsonElement,
): JsonObject {
    if ('/' !in path) return JsonObject(this + (path to value))
    val property = path.substringBefore('/')
    val key = path.substringAfter('/')
    val inner = (get(property) as? JsonObject).orEmpty()
    return JsonObject(this + (property to JsonObject(if (value is JsonNull) inner - key else inner + (key to value))))
}

private fun JsonObject.only(properties: Set<String>?): JsonObject =
    if (properties == null) this else JsonObject(filterKeys { it in properties })

private fun JsonObject.ids(): List<String>? = (get("ids") as? JsonArray)?.strings()

private fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }

private val JsonElement.id: String get() = jsonObject.getValue("id").jsonPrimitive.content
private val JsonObject.threadId: String get() = get("threadId")?.jsonPrimitive?.content ?: id
private val JsonObject.receivedAt: String get() = getValue("receivedAt").jsonPrimitive.content
private val JsonObject.boxes: Set<String> get() = (get("mailboxIds") as? JsonObject)?.keys.orEmpty()
