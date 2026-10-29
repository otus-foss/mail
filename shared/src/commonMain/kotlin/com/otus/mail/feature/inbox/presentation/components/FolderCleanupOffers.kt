package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/** Not spam, Restore, or Delete forever for [messages]; null for any other action. */
internal fun MessageActions.offerCleanup(
    messages: List<MailMessage>,
    action: BarButton,
): OfferedAction? =
    when (action) {
        MailAction.NotSpam -> offerNotSpam(messages)
        MailAction.Restore -> offerRestore(messages)
        MailAction.DeleteForever -> offerDeleteForever(messages)
        else -> null
    }

/** Moves the messages shown in Spam to their inbox, marked as not spam. */
private fun MessageActions.offerNotSpam(messages: List<MailMessage>): OfferedAction? =
    cleanup?.let { offerToInbox(messages, FolderRole.Junk, MailAction.NotSpam, it.onNotSpam) }

/** Moves the messages shown in Trash to their inbox; where they were before is not stored. */
private fun MessageActions.offerRestore(messages: List<MailMessage>): OfferedAction? =
    offerToInbox(messages, FolderRole.Trash, MailAction.Restore, moves.onMove)

private fun MessageActions.offerToInbox(
    messages: List<MailMessage>,
    role: FolderRole,
    action: MailAction,
    move: (MailMessage, MoveTarget) -> Unit,
): OfferedAction? {
    val moving =
        messages
            .filter { cleanup?.role?.invoke(it) == role }
            .mapNotNull { message -> shortcutTarget(message, FolderShortcut.Inbox)?.let { message to it } }
    if (moving.isEmpty()) return null
    return OfferedAction(action.label, action.symbol, leaves = true) {
        moving.forEach { (message, inbox) -> move(message, inbox) }
    }
}

/** Deletes the messages in Spam or Trash for good, once the user confirms it. */
private fun MessageActions.offerDeleteForever(messages: List<MailMessage>): OfferedAction? {
    val actions = cleanup ?: return null
    val deleting = messages.filter(actions.canDelete)
    val title = if (deleting.size == 1) "Delete this message forever?" else "Delete ${deleting.size} messages forever?"
    val question = ConfirmQuestion(title, "You can't undo this.", "Delete")
    return if (deleting.isEmpty()) {
        null
    } else {
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
    val folder = if (emptying.role == FolderRole.Junk) "spam" else "trash"
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
