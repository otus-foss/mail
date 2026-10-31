package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.feature.inbox.domain.AnsweredMessage

/** Deletes a sent draft's earlier copy [draftId] and marks the message it [answered]. */
internal suspend fun MailAccount.tidySent(
    draftId: String?,
    answered: AnsweredMessage?,
) {
    val update = answered?.let { mapOf(it.emailId to membershipPatch("keywords", it.keyword, member = true)) }
    if (draftId == null && update == null) return
    // The message is already sent, so a leftover draft or a missing mark is not a failure.
    bestEffort {
        connection.call(SetMethod(EmailType, id, update = update.orEmpty(), destroy = listOfNotNull(draftId)))
    }
}

internal suspend fun bestEffort(block: suspend () -> Unit) {
    try {
        block()
    } catch (expected: JmapException) {
        // The caller already knows the outcome that matters; this step only tidies up.
    }
}

/** Method errors (RFC 8620, section 3.6.2) that a later try of the same call may not get. */
internal val TEMPORARY_METHOD_ERRORS = setOf("serverUnavailable", "serverFail", "serverPartialFail")
