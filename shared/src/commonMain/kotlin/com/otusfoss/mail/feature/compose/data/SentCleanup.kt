package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.keywordPatch
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.keyword
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.AnsweredMessage

/**
 * Deletes a sent draft's earlier copy [draftId] and marks the message it [answered] through
 * [changes], which shows the mark in the cached lists and keeps it until the server has it.
 */
internal suspend fun MailAccount.tidySent(
    draftId: String?,
    answered: AnsweredMessage?,
    changes: EmailChangeQueue,
) {
    // The message is already sent, so a leftover draft is not a failure; the next save or send of
    // a draft does not see it either, and the user can delete it.
    if (draftId != null) jmapCallOr(Unit) { connection.call(SetMethod(EmailType, id, destroy = listOf(draftId))) }
    if (answered != null) changes.mark(login.id, answered, on = true)
}

/** Sets or clears the mark of the message a send [answered]. */
internal suspend fun EmailChangeQueue.mark(
    loginId: String,
    answered: AnsweredMessage,
    on: Boolean,
) {
    update(loginId, mapOf(answered.emailId to keywordPatch(answered.keyword, on)))
}

/**
 * [answered] when its original lacks the mark a send gives it, so canceling a held or scheduled
 * send takes back only a mark that send added. Null when the original had it already, or when the
 * server could not say, which leaves the mark as it is.
 */
internal suspend fun MailAccount.unmarked(answered: AnsweredMessage): AnsweredMessage? {
    val original =
        jmapCallOr(null) {
            connection.call(GetMethod(EmailType, id, JmapIds.Of(listOf(answered.emailId)), KEYWORD_PROPERTIES)).list
        }?.singleOrNull() ?: return null
    return answered.takeIf { original.keywords[answered.keyword] != true }
}

/** What reading an email's keywords asks for; an [com.otusfoss.mail.core.mail.EmailObject] needs `receivedAt`. */
internal val KEYWORD_PROPERTIES = listOf("id", "keywords", "receivedAt")
