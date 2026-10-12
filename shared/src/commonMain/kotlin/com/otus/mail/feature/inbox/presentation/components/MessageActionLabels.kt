package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.SwipeAction
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.ReadLaterChange

/** What pressing the button does next: an unread message offers to mark it read, and back. */
internal fun MessageFlag.label(on: Boolean): String =
    when (this) {
        MessageFlag.Read -> if (on) "Mark unread" else "Mark read"
        MessageFlag.Flagged -> if (on) "Unflag" else "Flag"
    }

internal val FolderShortcut.label: String
    get() =
        when (this) {
            FolderShortcut.Archive -> "Archive"
            FolderShortcut.Trash -> "Trash"
            FolderShortcut.Spam -> "Spam"
        }

/** Saving offers Read later; a saved message offers to leave it. */
internal val ReadLaterChange.label: String
    get() = if (saves) "Read later" else "Remove from Read later"

/** The action as the settings page names it, independent of any one message. */
internal val MailAction.label: String
    get() =
        when (this) {
            MailAction.Read -> "Read / unread"
            MailAction.Flag -> "Flag / unflag"
            MailAction.ReadLater -> "Read later"
            MailAction.Archive -> "Archive"
            MailAction.Trash -> "Trash"
            MailAction.Spam -> "Spam"
            MailAction.Move -> "Move"
            MailAction.RuleForSender -> "Rule for sender"
            MailAction.Reply -> "Reply"
            MailAction.ReplyAll -> "Reply all"
            MailAction.Forward -> "Forward"
            MailAction.NewMail -> "New mail"
            MailAction.Search -> "Search"
            MailAction.Filters -> "Filters"
            MailAction.MarkAllRead -> "Mark all read"
            MailAction.Send -> "Send"
            MailAction.Attach -> "Attach"
            MailAction.Discard -> "Discard"
        }

/** The action's name on a button that shows no state, such as an unavailable or previewed one. */
internal val MailAction.buttonLabel: String
    get() =
        when (this) {
            MailAction.Read -> MessageFlag.Read.label(on = false)
            MailAction.Flag -> MessageFlag.Flagged.label(on = false)
            else -> label
        }

/** The swipe that runs [action] on [message]; null when there is none or the message cannot take it. */
internal fun MessageActions.swipeAction(
    message: MailMessage,
    action: MailAction?,
): SwipeAction? =
    action?.takeIf { it.swipeable }?.let { offer(listOf(message), it) { _, _ -> } }?.let {
        SwipeAction(it.label, it.symbol, dismisses = it.leaves, onSwipe = it.run)
    }

/** The action bar mode as settings name it. */
internal val ActionBarMode.label: String
    get() =
        when (this) {
            ActionBarMode.Adaptive -> "Per screen"
            ActionBarMode.Fixed -> "Same everywhere"
        }

internal val ActionBarMode.description: String
    get() =
        when (this) {
            ActionBarMode.Adaptive -> "Each screen shows its own buttons"
            ActionBarMode.Fixed -> "Every screen shows the same buttons"
        }
