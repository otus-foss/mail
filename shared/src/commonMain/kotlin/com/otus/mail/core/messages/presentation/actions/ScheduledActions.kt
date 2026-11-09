package com.otus.mail.core.messages.presentation.actions

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage

/** What a message waiting in Scheduled can do: open in Write with its time ([edit]), or go now ([sendNow]). */
class ScheduledActions(
    val edit: (MailMessage) -> Unit,
    val sendNow: (MailMessage) -> Unit,
)

/**
 * Edit and Send now of a message waiting in Scheduled; null elsewhere or without
 * [PageSubject.scheduled]. Edit keeps the message where it is, so Write opens over its page.
 */
internal fun PageSubject.offerScheduled(action: MailAction): OfferedAction? {
    val actions = scheduled?.takeIf { role == MailboxRole.Scheduled } ?: return null
    val edits = action == MailAction.EditScheduled
    val run = if (edits) actions.edit else actions.sendNow
    return OfferedAction(action.label, action.symbol, leaves = !edits) { run(message) }
}
