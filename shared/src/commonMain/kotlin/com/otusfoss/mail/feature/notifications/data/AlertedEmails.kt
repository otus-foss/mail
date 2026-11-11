package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.isDraft
import com.otusfoss.mail.core.mail.isSeen
import com.otusfoss.mail.core.mail.received
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.displayLine
import com.otusfoss.mail.core.messages.domain.displayText
import com.otusfoss.mail.feature.notifications.domain.NewMail
import kotlin.time.Clock

internal fun inboxIds(folders: List<MailFolder>): Set<String> =
    folders
        .filter { it.role == MailboxRole.Inbox }
        .map { it.id }
        .toSet()

/** Unread, not a draft, and in one of [inboxes]: mail worth an alert. */
internal fun EmailObject.waitsIn(inboxes: Set<String>): Boolean =
    mailboxIds.any { it.value && it.key in inboxes } &&
        !isSeen &&
        !isDraft

/** The alert for this email, its text cleaned as the inbox cleans it, so a sender cannot fake another. */
internal fun EmailObject.toNewMail(account: MailAccount): NewMail {
    val from = from?.firstOrNull()
    return NewMail(
        loginId = account.login.id,
        accountName = account.login.name,
        emailId = id,
        sender =
            listOfNotNull(from?.name, from?.email)
                .map { it.displayLine() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty(),
        subject = subject.orEmpty().displayLine(),
        preview = preview.displayText(),
        // One malformed date must not stop every later check.
        receivedAt = received ?: Clock.System.now(),
        recipients = (to.orEmpty() + cc.orEmpty()).map { it.email } + deliveredAddresses,
    )
}
