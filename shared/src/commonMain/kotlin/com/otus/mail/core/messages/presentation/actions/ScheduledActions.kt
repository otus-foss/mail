package com.otus.mail.core.messages.presentation.actions

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage

/** What a message waiting in Scheduled can do: go back to Write with its time ([edit]), or go now ([sendNow]). */
class ScheduledActions(
    val edit: (MailMessage) -> Unit,
    val sendNow: (MailMessage) -> Unit,
)

/** Edit and Send now of a message waiting in Scheduled; null elsewhere or without [PageSubject.scheduled]. */
internal fun PageSubject.offerScheduled(action: MailAction): OfferedAction? {
    val actions = scheduled?.takeIf { role == MailboxRole.Scheduled } ?: return null
    val run = if (action == MailAction.EditScheduled) actions.edit else actions.sendNow
    return OfferedAction(action.label, action.symbol, leaves = true) { run(message) }
}
