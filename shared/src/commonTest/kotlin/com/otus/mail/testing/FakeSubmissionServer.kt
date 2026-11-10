package com.otus.mail.testing

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.mailAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** An email submission of [emailId], in [undoStatus], that goes at [sendAt]. */
internal data class FakeSubmission(
    val emailId: String,
    val undoStatus: String,
    val sendAt: Instant,
)

/**
 * A server for filing scheduled mail. Each email in [copies] is in the mailboxes it maps to and has
 * `$draft` when it is in [drafts]. It answers `Email/query` by mailbox and `notKeyword`, follows
 * back-references, and records the `Email/set` updates in [updates]. `EmailSubmission/query` stops
 * at [submissionLimit] when set. `Mailbox/set` gives a new folder the id `made`, and refuses
 * creations and updates when [refuseFolders] is true.
 */
internal class FakeSubmissionServer(
    private val copies: Map<String, Set<String>>,
    private val submissions: List<FakeSubmission> = emptyList(),
    private val drafts: Set<String> = emptySet(),
    private val submissionLimit: Int? = null,
    private val refuseFolders: Boolean = false,
) {
    val calls = mutableListOf<String>()
    val updates = mutableMapOf<String, JsonElement>()
    val folderChanges = mutableListOf<JsonObject>()

    suspend fun account(): MailAccount {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        return JmapConnections(JmapClient(stalwartLike(SUBMISSION_SESSION, ::reply)), store).mailAccount(ADA_LOGIN)
    }

    private fun reply(body: String): String {
        calls += body
        val results = mutableMapOf<String, JsonObject>()
        val responses =
            JsonArray(
                parse(body).getValue("methodCalls").jsonArray.map { call ->
                    val (method, arguments, callId) = call.jsonArray
                    val name = method.jsonPrimitive.content
                    val id = callId.jsonPrimitive.content
                    val answer = resolved(arguments.jsonObject, results)?.let { answer(name, it) }
                    answer?.let { results[id] = it }
                    val error = buildJsonObject { put("type", "serverFail") }
                    buildJsonArray {
                        add(JsonPrimitive(if (answer == null) "error" else name))
                        add(answer ?: error)
                        add(callId)
                    }
                },
            )
        return buildJsonObject { put("methodResponses", responses) }.toString()
    }

    private fun answer(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Email/query" -> query(emailsMatching(arguments.getValue("filter").jsonObject))
            "EmailSubmission/query" -> submissionQuery(arguments.getValue("filter").jsonObject)
            "EmailSubmission/get" -> list(arguments.ids().map(::submission))
            "Email/get" -> list(arguments.ids().filter { it in copies }.map(::copy))
            "Mailbox/set" -> folderSet(arguments)
            "Email/set" -> emailSet(arguments)
            else -> null
        }

    private fun emailsMatching(filter: JsonObject): List<String> {
        val box = mailboxesIn(filter).single()
        val withoutDraft = "notKeyword" in filter.toString()
        return copies.filter { (id, boxes) -> box in boxes && !(withoutDraft && id in drafts) }.keys.toList()
    }

    private fun submissionQuery(filter: JsonObject): JsonObject {
        val text = filter.toString()
        val matching =
            submissions.indices.filter { index ->
                val submission = submissions[index]
                if ("undoStatus" in text) submission.undoStatus == "pending" else "\"${submission.emailId}\"" in text
            }
        val found = submissionLimit?.let(matching::take) ?: matching
        return query(found.map { "s$it" }, limit = submissionLimit)
    }

    private fun submission(id: String): JsonObject {
        val submission = submissions[id.removePrefix("s").toInt()]
        return buildJsonObject {
            put("id", id)
            put("emailId", submission.emailId)
            put("undoStatus", submission.undoStatus)
            put("sendAt", submission.sendAt.toString())
        }
    }

    private fun copy(id: String): JsonObject =
        buildJsonObject {
            put("id", id)
            put("mailboxIds", JsonObject(copies.getValue(id).associateWith { JsonPrimitive(true) }))
            put("receivedAt", "2026-11-10T11:00:00Z")
        }

    private fun folderSet(arguments: JsonObject): JsonObject {
        folderChanges += arguments
        val refusal = buildJsonObject { put("type", "forbidden") }
        val creates = (arguments["create"] as? JsonObject)?.keys.orEmpty()
        val changes = (arguments["update"] as? JsonObject)?.keys.orEmpty()
        return buildJsonObject {
            put("newState", "m2")
            if (refuseFolders) {
                put("notCreated", JsonObject(creates.associateWith { refusal }))
                put("notUpdated", JsonObject(changes.associateWith { refusal }))
            } else {
                put("created", JsonObject(creates.associateWith { buildJsonObject { put("id", "made") } }))
                put("updated", JsonObject(changes.associateWith { JsonNull }))
            }
        }
    }

    private fun emailSet(arguments: JsonObject): JsonObject {
        val update = arguments.getValue("update").jsonObject
        updates += update
        return buildJsonObject {
            put("newState", "e2")
            put("updated", JsonObject(update.mapValues { JsonNull }))
        }
    }

    private fun query(
        ids: List<String>,
        limit: Int? = null,
    ) = buildJsonObject {
        put("queryState", "q")
        put("ids", JsonArray(ids.map(::JsonPrimitive)))
        put("position", 0)
        limit?.let { put("limit", it) }
    }

    private fun list(items: List<JsonObject>) =
        buildJsonObject {
            put("state", "s")
            put("list", JsonArray(items))
        }

    private fun JsonObject.ids(): List<String> = getValue("ids").jsonArray.map { it.jsonPrimitive.content }

    private fun parse(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject
}
