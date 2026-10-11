package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.SwipeAction
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageSwipe
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import org.jetbrains.compose.resources.DrawableResource

/** What pressing the button does next: an unread message offers to mark it read, and back. */
internal fun MessageFlag.label(on: Boolean): String =
    when (this) {
        MessageFlag.Read -> if (on) "Mark unread" else "Mark read"
        MessageFlag.Flagged -> if (on) "Unflag" else "Flag"
    }

internal val FolderShortcut.label: String
    get() =
        when (this) {
            FolderShortcut.ReadLater -> "Read later"
            FolderShortcut.Archive -> "Archive"
            FolderShortcut.Trash -> "Trash"
        }

/** The choice as the settings page names it, independent of any one message. */
internal val MessageSwipe.label: String
    get() =
        when (this) {
            MessageSwipe.None -> "Nothing"
            MessageSwipe.Read -> "Read / unread"
            MessageSwipe.Flag -> "Flag / unflag"
            MessageSwipe.ReadLater -> "Read later"
            MessageSwipe.Archive -> "Archive"
            MessageSwipe.Trash -> "Trash"
        }

/** The swipe that runs [swipe] on [message]; null when the message cannot take that action. */
internal fun MessageActions.swipeAction(
    message: MailMessage,
    swipe: MessageSwipe,
): SwipeAction? =
    when (swipe) {
        MessageSwipe.None -> null
        MessageSwipe.Read -> markSwipe(message, MessageFlag.Read)
        MessageSwipe.Flag -> markSwipe(message, MessageFlag.Flagged)
        MessageSwipe.ReadLater -> fileSwipe(message, FolderShortcut.ReadLater, Res.drawable.symbol_bookmark)
        MessageSwipe.Archive -> fileSwipe(message, FolderShortcut.Archive, Res.drawable.symbol_archive)
        MessageSwipe.Trash -> fileSwipe(message, FolderShortcut.Trash, Res.drawable.symbol_delete)
    }

private fun MessageActions.markSwipe(
    message: MailMessage,
    flag: MessageFlag,
): SwipeAction? {
    if (!canMark(message, flag)) return null
    val on = message.hasFlag(flag)
    val icon =
        when (flag) {
            MessageFlag.Read -> if (on) Res.drawable.symbol_mark_email_unread else Res.drawable.symbol_mark_email_read
            MessageFlag.Flagged -> Res.drawable.symbol_flag
        }
    return SwipeAction(flag.label(on), icon) { onMark(message, flag, !on) }
}

private fun MessageActions.fileSwipe(
    message: MailMessage,
    shortcut: FolderShortcut,
    icon: DrawableResource,
): SwipeAction? =
    shortcutTarget(message, shortcut)?.let { target ->
        SwipeAction(shortcut.label, icon, dismisses = true) { onMove(message, target) }
    }
