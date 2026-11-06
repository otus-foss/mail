package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.swipe.components.SwipeAction
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.ReadLaterChange

/** What pressing the button does next: an unread message offers to mark it read, and back. */
internal fun MessageFlag.label(on: Boolean): String =
    when (this) {
        MessageFlag.Read -> if (on) "Mark unread" else "Mark read"
        MessageFlag.Flagged -> if (on) "Unflag" else "Flag"
    }

/** Saving offers Read later; a saved message offers to leave it. */
internal val ReadLaterChange.label: String
    get() = if (saves) "Read later" else "Remove from Read later"

/** The button as the settings page names it, independent of any one message. */
internal val BarButton.label: String
    get() =
        when (this) {
            is BarButton.MoveTo -> "Move to ${path.joinToString(FOLDER_PATH_SEPARATOR)}"
            MailAction.Read -> "Read / unread"
            MailAction.Flag -> "Flag / unflag"
            MailAction.ReadLater -> "Read later"
            MailAction.Archive -> "Archive"
            MailAction.Trash -> "Delete"
            MailAction.Spam -> "Spam"
            MailAction.NotSpam -> "Not spam"
            MailAction.Restore -> "Restore"
            MailAction.DeleteForever -> "Delete forever"
            MailAction.Move -> "Move"
            MailAction.SuggestedMove -> "Suggested move"
            MailAction.RuleForSender -> "Rule for sender"
            MailAction.Reply -> "Reply"
            MailAction.ReplyAll -> "Reply all"
            MailAction.Forward -> "Forward"
            MailAction.FollowUp -> "Follow up"
            MailAction.NewMail -> "New mail"
            MailAction.Search -> "Search"
            MailAction.Filters -> "Filters"
            MailAction.MarkAllRead -> "Mark all read"
            MailAction.EmptyFolder -> "Empty spam or trash"
            MailAction.Send -> "Send"
            MailAction.Attach -> "Attach"
            MailAction.Discard -> "Discard"
            MailAction.AllActions -> "All actions"
        }

/**
 * The button's name in a bar when it shows no state, such as an unavailable or previewed one. On
 * the [primary] button, Move to shows only the folder's name.
 */
internal fun BarButton.buttonLabel(primary: Boolean = false): String =
    when (this) {
        is BarButton.MoveTo -> if (primary) name else "Move to $name"
        MailAction.Read -> MessageFlag.Read.label(on = false)
        MailAction.Flag -> MessageFlag.Flagged.label(on = false)
        else -> label
    }

/** Joins a folder's names from the top level down, as pickers show its path. */
internal const val FOLDER_PATH_SEPARATOR = " / "

/** The swipe that runs [action] on [message]; null when there is none or the message cannot take it. */
internal fun MessageActions.swipeAction(
    message: MailMessage,
    action: MailAction?,
): SwipeAction? =
    action?.takeIf { it.swipeable }?.let { offer(listOf(message), it, chooseFolder = { _, _ -> }) }?.let {
        SwipeAction(UiAction(it.label, it.symbol, onClick = it.run), dismisses = it.leaves)
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
