package com.otus.mail.feature.compose.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.jmap.jmapPatch
import com.otus.jmap.noneOf
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.boxes
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The mailboxes of each copy, by email id, whose submission is pending (RFC 8621, section 7) and
 * goes later than [now] plus [UNDO_HOLD], so a send Mail holds for Undo does not count.
 */
internal suspend fun MailAccount.scheduledCopies(now: Instant): Map<String, Set<String>> {
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    lateinit var copies: JmapCall<GetResult<EmailObject>>
    val response =
        connection.send {
            val pending =
                call(QueryMethod(EmailSubmissionType, id, filterCondition("undoStatus", JsonPrimitive(PENDING))))
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(pending.resultOf("/ids")),
                        listOf("emailId", "sendAt"),
                    ),
                )
            copies =
                call(
                    GetMethod(
                        EmailType,
                        id,
                        JmapIds.Result(submissions.resultOf("/list/*/emailId")),
                        listOf("id", "mailboxIds", "receivedAt"),
                    ),
                )
        }
    val later =
        response[submissions]
            .list
            .filter { submission -> submission.instant("sendAt")?.let { it > now + UNDO_HOLD } == true }
            .mapNotNull { it.text("emailId") }
            .toSet()
    return response[copies]
        .list
        .filter { it.id in later }
        .associate { it.id to it.boxes }
}

/** The `undoStatus` of the live submission of each of [emailIds] that has one. */
internal suspend fun MailAccount.submissionStatus(emailIds: List<String>): Map<String, String> {
    if (emailIds.isEmpty()) return emptyMap()
    lateinit var live: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    val response =
        connection.send {
            live =
                call(
                    QueryMethod(
                        EmailSubmissionType,
                        id,
                        allOf(
                            filterCondition("emailIds", JsonArray(emailIds.map(::JsonPrimitive))),
                            noneOf(filterCondition("undoStatus", JsonPrimitive("canceled"))),
                        ),
                    ),
                )
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(live.resultOf("/ids")),
                        listOf("emailId", "undoStatus"),
                    ),
                )
        }
    return response[submissions]
        .list
        .mapNotNull { submission ->
            val email = submission["emailId"]?.jsonPrimitive?.contentOrNull
            val undo = submission["undoStatus"]?.jsonPrimitive?.contentOrNull
            if (email != null && undo != null && email in emailIds) email to undo else null
        }.toMap()
}

/** The submission of [emailId] the server still holds, with its identity and when it goes; null when none waits. */
internal suspend fun MailAccount.pendingSubmission(emailId: String): PendingSubmission? {
    lateinit var waiting: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    val response =
        connection.send {
            waiting =
                call(
                    QueryMethod(
                        EmailSubmissionType,
                        id,
                        allOf(
                            filterCondition("emailIds", JsonArray(listOf(JsonPrimitive(emailId)))),
                            filterCondition("undoStatus", JsonPrimitive(PENDING)),
                        ),
                        limit = 1,
                    ),
                )
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(waiting.resultOf("/ids")),
                        listOf("id", "identityId", "sendAt"),
                    ),
                )
        }
    val submission = response[submissions].list.firstOrNull()
    val id = submission?.text("id")
    val at = submission?.instant("sendAt")
    return if (id != null && at != null) PendingSubmission(id, submission.text("identityId").orEmpty(), at) else null
}

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.instant(name: String): Instant? =
    text(name)?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

/** A held submission [id], sent as [identityId] at [at]. */
internal data class PendingSubmission(
    val id: String,
    val identityId: String,
    val at: Instant,
)

private const val PENDING = "pending"

/** Longer than the longest time Mail holds a send for Undo, 30 seconds. */
private val UNDO_HOLD = 1.minutes
