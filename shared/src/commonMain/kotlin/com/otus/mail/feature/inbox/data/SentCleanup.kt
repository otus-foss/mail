package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapIds
import com.otus.jmap.SetMethod
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.keywordPatch
import com.otus.mail.core.messages.data.keyword
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.feature.inbox.domain.AnsweredMessage

/** Deletes a sent draft's earlier copy [draftId] and marks the message it [answered]. */
internal suspend fun MailAccount.tidySent(
    draftId: String?,
    answered: AnsweredMessage?,
) {
    val update = answered?.let { mapOf(it.emailId to keywordPatch(it.keyword, true)) }
    if (draftId == null && update == null) return
    // The message is already sent, so a leftover draft or a missing mark is not a failure.
    jmapCallOr(Unit) {
        connection.call(SetMethod(EmailType, id, update = update.orEmpty(), destroy = listOfNotNull(draftId)))
    }
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

/** What reading an email's keywords asks for; an [com.otus.mail.core.mail.EmailObject] needs `receivedAt`. */
internal val KEYWORD_PROPERTIES = listOf("id", "keywords", "receivedAt")
