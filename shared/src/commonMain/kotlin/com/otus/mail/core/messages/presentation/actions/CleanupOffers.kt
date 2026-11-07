package com.otus.mail.core.messages.presentation.actions

import com.otus.designsystem.shared.overlays.components.ConfirmQuestion
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MoveTarget

/** Moves the messages shown in Spam to their inbox, marked as not spam. */
internal fun MessageActions.offerNotSpam(messages: List<MailMessage>): OfferedAction? =
    cleanup?.let { offerToInbox(messages, MailboxRole.Junk, MailAction.NotSpam, it.onNotSpam) }

/** Moves the messages shown in Trash to their inbox; where they were before is not stored. */
internal fun MessageActions.offerRestore(messages: List<MailMessage>): OfferedAction? =
    offerToInbox(messages, MailboxRole.Trash, MailAction.Restore, moves.onMove)

private fun MessageActions.offerToInbox(
    messages: List<MailMessage>,
    role: MailboxRole,
    action: MailAction,
    move: (MailMessage, MoveTarget) -> Unit,
): OfferedAction? {
    val moving =
        messages
            .filter { cleanup?.role?.invoke(it) == role }
            .mapNotNull { message -> shortcutTarget(message, MailboxRole.Inbox)?.let { message to it } }
    return moving.takeIf { it.isNotEmpty() }?.let {
        OfferedAction(action.label, action.symbol, leaves = true) {
            moving.forEach { (message, inbox) -> move(message, inbox) }
        }
    }
}

/** Deletes the messages in Spam or Trash for good, once the user confirms it. */
internal fun MessageActions.offerDeleteForever(messages: List<MailMessage>): OfferedAction? {
    val actions = cleanup ?: return null
    val deleting = messages.filter(actions.canDelete)
    val title = if (deleting.size == 1) "Delete this message forever?" else "Delete ${deleting.size} messages forever?"
    val question = ConfirmQuestion(title, "You can't undo this.", "Delete")
    return deleting.takeIf { it.isNotEmpty() }?.let {
        OfferedAction(
            MailAction.DeleteForever.label,
            MailAction.DeleteForever.symbol,
            leaves = true,
            confirm = question,
        ) {
            actions.onDelete(deleting)
        }
    }
}

/** Empties the list's Spam or Trash folders, once the user confirms it; null when they hold nothing. */
internal fun MessageActions.offerEmpty(): OfferedAction? {
    val emptying = cleanup?.emptying()?.takeIf { it.count > 0 } ?: return null
    val folder = if (emptying.role == MailboxRole.Junk) "spam" else "trash"
    val noun = if (emptying.count == 1L) "message" else "messages"
    val question =
        ConfirmQuestion("Empty $folder?", "This deletes ${emptying.count} $noun forever. You can't undo this.", "Empty")
    return OfferedAction(
        "Empty $folder",
        MailAction.EmptyFolder.symbol,
        leaves = false,
        confirm = question,
        run = emptying.run,
    )
}
