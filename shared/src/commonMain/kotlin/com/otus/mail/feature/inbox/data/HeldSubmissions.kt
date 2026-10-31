package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetError
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.condition
import com.otus.mail.core.mail.operator
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SentMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
        ?: SendFailure.ServerProblem.takeIf { message.submissionId !in undone.updated }
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
 * section 7.3), for a request whose answer was lost. False when it could not say.
 */
internal suspend fun MailAccount.isSubmitted(emailId: String): Boolean =
    unlessUnanswered {
        connection
            .call(
                QueryMethod(
                    EmailSubmissionType,
                    id,
                    operator(
                        "AND",
                        listOf(
                            condition("emailIds", JsonArray(listOf(JsonPrimitive(emailId)))),
                            operator("NOT", listOf(condition("undoStatus", JsonPrimitive("canceled")))),
                        ),
                    ),
                    limit = 1,
                ),
            ).ids
            .isNotEmpty()
    } == true

/** [read]'s result, or null when the server could not be reached or refused the call. */
internal inline fun <T> unlessUnanswered(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException) {
        null
    }

private const val RESUBMISSION = "resend"
