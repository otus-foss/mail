package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.QueryResult
import com.otusfoss.core.jmap.allOf
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailSubmissionType
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.messages.data.boxes
import com.otusfoss.mail.core.messages.domain.SERVER_SPARE
import com.otusfoss.mail.core.settings.domain.UNDO_SEND_CHOICES
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
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

/** What became of a copy's send, by its submissions; the first that fits wins. */
internal enum class SendState {
    /** A submission still waits to go. */
    Pending,

    /** A submission went (`undoStatus` `final`). */
    Sent,

    /** Every submission was canceled. */
    Canceled,

    /** The server keeps no submission of it, as when it destroyed one that went. */
    Gone,
}

/**
 * The [SendState] of each of [emailIds] that has a submission, from all of them, canceled ones
 * too; an email left out has none. Null when the server cut the answer short at its limit, so a
 * missing email may still have one.
 */
internal suspend fun MailAccount.sendStates(emailIds: List<String>): Map<String, SendState>? {
    if (emailIds.isEmpty()) return emptyMap()
    lateinit var found: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    val response =
        connection.send {
            found =
                call(
                    QueryMethod(
                        EmailSubmissionType,
                        id,
                        filterCondition("emailIds", JsonArray(emailIds.map(::JsonPrimitive))),
                    ),
                )
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(found.resultOf("/ids")),
                        listOf("emailId", "undoStatus"),
                    ),
                )
        }
    val query = response[found]
    val cutShort = query.limit?.let { query.ids.size >= it } == true
    return response[submissions]
        .list
        .mapNotNull { submission ->
            val email = submission.text("emailId")?.takeIf { it in emailIds }
            val state = UNDO_STATES[submission.text("undoStatus")]
            if (email != null && state != null) email to state else null
        }.groupBy({ it.first }, { it.second })
        .mapValues { (_, states) -> states.min() }
        .takeUnless { cutShort }
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

private val UNDO_STATES =
    mapOf(
        PENDING to SendState.Pending,
        "final" to SendState.Sent,
        "canceled" to SendState.Canceled,
    )

private val CLOCK_SLACK = 30.seconds

/**
 * Longer than any send Mail holds for Undo, with slack for another device's clock and for servers
 * that round the hold up: a send scheduled from elsewhere for less than this stays in Sent.
 */
private val UNDO_HOLD = UNDO_SEND_CHOICES.max().seconds + SERVER_SPARE + CLOCK_SLACK
