package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.mail.utcDate
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.WaitingDraft
import kotlin.time.Instant

/**
 * A draft that waits on the device as the Email a Drafts list shows for it, in [draftsId], under
 * its device id. It arrived when it was saved, so it sorts among the server's drafts by that time.
 */
internal fun WaitingDraft.toEmail(draftsId: String): EmailObject =
    EmailObject(
        id = id,
        mailboxIds = mapOf(draftsId to true),
        keywords = mapOf(Keywords.DRAFT to true, Keywords.SEEN to true),
        from = listOfNotNull(draft.from?.toAddress()),
        to = draft.to.map { it.toAddress() },
        cc = draft.cc.map { it.toAddress() },
        bcc = draft.bcc.map { it.toAddress() },
        subject = draft.subject,
        receivedAt = Instant.fromEpochMilliseconds(savedMillis).utcDate(),
        preview = draft.body.take(PREVIEW_CHARACTERS),
        hasAttachment = draft.attachments.any { !it.inline },
    )

private fun MailSender.toAddress() = EmailAddress(name.takeIf { it.isNotBlank() && it != address }, address)

/** As much of the body as a server's preview holds (RFC 8621, section 4.1.4). */
private const val PREVIEW_CHARACTERS = 256
