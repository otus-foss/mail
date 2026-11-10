package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetError
import com.otus.jmap.SetMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.jmap.noneOf
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.domain.DraftRestoreException
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Cancels [message]'s submission; why not, when the server did not confirm it. */
internal suspend fun MailAccount.cancelSubmission(message: SentMessage): SendFailure? {
    val undone =
        connection.call(
            SetMethod(
                EmailSubmissionType,
                id,
                update = mapOf(message.submissionId to buildJsonObject { put("undoStatus", "canceled") }),
            ),
        )
    return undone.notUpdated[message.submissionId]?.toSendFailure()
        ?: SendFailure.Mail(MailFailure.ServerProblem).takeIf { message.submissionId !in undone.updated }
}

/** Submits [message]'s stored copy again, without a hold; why the server refused, or null. */
internal suspend fun MailAccount.resubmit(message: SentMessage): SetError? =
    connection
        .call(
            SetMethod(
                EmailSubmissionType,
                id,
                create =
                    mapOf(
                        RESUBMISSION to
                            buildJsonObject {
                                put("emailId", message.emailId)
                                put("identityId", message.identityId)
                            },
                    ),
            ),
        ).notCreated[RESUBMISSION]

/**
 * Whether the server holds or sent a submission of [emailId] that nobody canceled (RFC 8621,
 * section 7.3), for a request whose answer was lost. Null when it could not say.
 */
internal suspend fun MailAccount.isSubmitted(emailId: String): Boolean? =
    jmapCallOr(null) { liveSubmissions(listOf(emailId), limit = 1).isNotEmpty() }

/**
 * Cancels every submission of [emailIds] that nobody canceled yet. True when there was one and the
 * server canceled them all, so none of the emails goes; false when one already went or the server
 * keeps it.
 */
internal suspend fun MailAccount.cancelHeld(emailIds: List<String>): Boolean {
    val live = liveSubmissions(emailIds)
    if (live.isEmpty()) return false
    val canceled = buildJsonObject { put("undoStatus", "canceled") }
    val undone = connection.call(SetMethod(EmailSubmissionType, id, update = live.associateWith { canceled }))
    return undone.updated.keys.containsAll(live)
}

private suspend fun MailAccount.liveSubmissions(
    emailIds: List<String>,
    limit: Int? = null,
): List<String> =
    connection
        .call(
            QueryMethod(
                EmailSubmissionType,
                id,
                allOf(
                    filterCondition("emailIds", JsonArray(emailIds.map(::JsonPrimitive))),
                    noneOf(filterCondition("undoStatus", JsonPrimitive("canceled"))),
                ),
                limit = limit,
            ),
        ).ids

/**
 * Marks the copy of a canceled [message] as a draft again, back in Drafts if it moved to Sent,
 * and then takes back the mark its send gave the original it answered.
 */
internal suspend fun MailAccount.restoreCanceledDraft(
    message: SentMessage,
    changes: EmailChangeQueue,
) {
    val emailId = message.emailId
    val patch =
        buildJsonObject {
            put("keywords/${Keywords.DRAFT}", true)
            if (message.draftsId != null) putJsonObject("mailboxIds") { put(message.draftsId, true) }
        }
    try {
        val restored = connection.call(SetMethod(EmailType, id, update = mapOf(emailId to patch)))
        if (emailId !in restored.updated || emailId in restored.notUpdated) throw DraftRestoreException()
    } catch (error: JmapException) {
        throw DraftRestoreException(error)
    }
    message.marked?.let { changes.mark(login.id, it, on = false) }
}

/**
 * [restoreCanceledDraft] for a send the server canceled. It stays stopped when the restore fails,
 * as filing Scheduled makes the copy a draft later.
 */
internal suspend fun MailAccount.restoredAfterCancel(
    message: SentMessage,
    changes: EmailChangeQueue,
): SendCancel {
    try {
        restoreCanceledDraft(message, changes)
    } catch (expected: DraftRestoreException) {
        // The send is gone all the same.
    }
    return SendCancel.Stopped
}

private const val RESUBMISSION = "send"
