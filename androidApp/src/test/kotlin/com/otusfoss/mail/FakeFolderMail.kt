package com.otusfoss.mail

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The mail of a [FakeFolderServer]: each mailbox holds one message from Grace Hopper, alone in its
 * thread. The inbox's is `m1` and any other mailbox's is `m1-<mailbox>`, titled by
 * [FakeFolderServer.subjects]. The server keeps no states, so `Email/changes`, `Thread/changes`,
 * and `Email/queryChanges` cannot be calculated and every load reads the lists again. Updates are
 * accepted, so tests read what was sent from [FakeFolderServer.calls]. Only keyword patches apply,
 * as on a real server, so a reload during a test keeps a message's read and flag marks.
 */
internal class FakeFolderMail(
    private val server: FakeFolderServer,
) {
    private var draftCount = 0
    private var keywords = mapOf<String, Set<String>>()

    fun answer(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Email/query" -> query(arguments)
            "Email/get" -> emails(arguments)
            "Thread/get" -> listResult("t", arguments.ids().orEmpty().mapNotNull(::thread))
            "Email/changes", "Thread/changes", "Email/queryChanges" -> null
            "Email/set" -> setEmails(arguments)
            else -> error("Unexpected call: $method")
        }

    /**
     * A sender's earlier mail for a `from` filter; mail a rule's `AND` filter finds; otherwise the
     * message of each mailbox the filter names, as a list's or count's `collapseThreads` query does.
     */
    private fun query(arguments: JsonObject): JsonObject {
        val filter = arguments["filter"] as? JsonObject ?: JsonObject(emptyMap())
        val boxes = mailboxesIn(filter)
        val ids =
            when {
                "from" in filter -> server.senderFolders.indices.map { "$HISTORY$it" }
                arguments["collapseThreads"] != TRUE && filter["operator"] == AND -> ruleMatches(boxes)
                else -> boxes.map(::emailIdIn)
            }
        val position = arguments["position"]?.jsonPrimitive?.int ?: 0
        val limit = arguments["limit"]?.jsonPrimitive?.int ?: ids.size
        return buildJsonObject {
            put("queryState", "q")
            put("ids", ids.drop(position).take(limit).toJsonArray())
            put("position", position)
            put("total", ids.size)
        }
    }

    /** The inbox mail a rule finds: [FakeFolderServer.olderMail], which the moved message already left. */
    private fun ruleMatches(boxes: Set<String>): List<String> =
        if (INBOX in boxes) server.olderMail.toList() else emptyList()

    private fun emails(arguments: JsonObject): JsonObject {
        val ids = arguments.ids().orEmpty()
        val properties = (arguments["properties"] as? JsonArray)?.strings().orEmpty()
        // An open message asks for its HTML body; these messages have none.
        val found = if ("htmlBody" in properties) emptyList() else ids.mapNotNull(::email)
        return buildJsonObject {
            put("state", "e")
            put("list", JsonArray(found))
            put("notFound", (ids - found.map { it.id }.toSet()).toJsonArray())
        }
    }

    private fun email(id: String): JsonObject? =
        when {
            id.startsWith(HISTORY) -> historyEmail(id)
            id == INBOX_EMAIL || id.startsWith(FOLDER_EMAIL) -> graceEmail(id, mailboxOf(id))
            else -> null
        }

    /** An earlier message from the sender, with only the folder [FakeFolderServer.senderFolders] gives it. */
    private fun historyEmail(id: String): JsonObject? =
        id.removePrefix(HISTORY).toIntOrNull()?.let(server.senderFolders::getOrNull)?.let { folder ->
            buildJsonObject {
                put("id", id)
                putJsonObject("mailboxIds") { put(folder, true) }
            }
        }

    private fun graceEmail(
        id: String,
        mailbox: String,
    ): JsonObject =
        buildJsonObject {
            put("id", id)
            put("threadId", "T$id")
            putJsonObject("mailboxIds") { (listOf(mailbox) + server.alsoIn).distinct().forEach { put(it, true) } }
            putJsonArray("from") {
                add(
                    buildJsonObject {
                        put("name", "Grace Hopper")
                        put("email", "grace@example.org")
                    },
                )
            }
            putJsonObject("keywords") { keywords[id].orEmpty().forEach { put(it, true) } }
            put("subject", server.subjects[mailbox] ?: "Welcome")
            put("receivedAt", "2026-10-04T09:30:00Z")
            put("preview", "Hello")
        }

    private fun thread(id: String): JsonObject? =
        email(id.removePrefix("T"))?.let { member ->
            buildJsonObject {
                put("id", id)
                putJsonArray("emailIds") { add(member.getValue("id")) }
            }
        }

    /**
     * Drafts get the ids `draft1`, `draft2`, and so on. Older mail a rule moves leaves
     * [FakeFolderServer.olderMail].
     */
    private fun setEmails(arguments: JsonObject): JsonObject =
        buildJsonObject {
            put("newState", "e2")
            (arguments["create"] as? JsonObject)?.let { creates ->
                put("created", JsonObject(creates.mapValues { buildJsonObject { put("id", "draft${++draftCount}") } }))
            }
            (arguments["update"] as? JsonObject)?.let { updates ->
                if (server.refuseUpdates) {
                    put("notUpdated", JsonObject(updates.mapValues { FORBIDDEN }))
                } else {
                    server.olderMail -= updates.keys
                    keywords += updates.mapValues { (id, patch) -> patched(keywords[id].orEmpty(), patch.jsonObject) }
                    put("updated", JsonObject(updates.mapValues { JsonNull }))
                }
            }
            (arguments["destroy"] as? JsonArray)?.let { put("destroyed", it) }
        }

    private companion object {
        const val INBOX = "a"
        const val INBOX_EMAIL = "m1"
        const val FOLDER_EMAIL = "m1-"
        const val HISTORY = "h"
        val TRUE = JsonPrimitive(true)
        val AND = JsonPrimitive("AND")
        val FORBIDDEN = buildJsonObject { put("type", "forbidden") }

        fun emailIdIn(mailbox: String) = if (mailbox == INBOX) INBOX_EMAIL else FOLDER_EMAIL + mailbox

        fun mailboxOf(emailId: String) = if (emailId == INBOX_EMAIL) INBOX else emailId.removePrefix(FOLDER_EMAIL)
    }
}

/** [keywords] after an `Email/set` [patch]: `keywords/<name>` set to true adds the keyword, null removes it. */
private fun patched(
    keywords: Set<String>,
    patch: JsonObject,
): Set<String> =
    patch
        .filterKeys { it.startsWith(KEYWORD_PATH) }
        .entries
        .fold(keywords) { kept, (path, value) ->
            val name = path.removePrefix(KEYWORD_PATH)
            if (value == JsonNull) kept - name else kept + name
        }

private const val KEYWORD_PATH = "keywords/"

/** The mailboxes a filter names: an AND's first condition holds them, and an OR lists them. */
private fun mailboxesIn(filter: JsonObject): Set<String> {
    val conditions = filter["conditions"]?.jsonArray.orEmpty().map { it.jsonObject }
    return when {
        "inMailbox" in filter -> setOf(filter.getValue("inMailbox").jsonPrimitive.content)
        filter["operator"] == JsonPrimitive("AND") -> conditions.firstOrNull()?.let(::mailboxesIn).orEmpty()
        else -> conditions.flatMap(::mailboxesIn).toSet()
    }
}
