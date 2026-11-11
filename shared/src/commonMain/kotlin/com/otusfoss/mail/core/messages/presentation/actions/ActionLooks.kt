package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_archive
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_auto_filter
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_drive_file_move
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_filter_list
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.resources.symbol_forward
import com.otusfoss.core.designsystem.resources.symbol_inbox
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.resources.symbol_more_horiz
import com.otusfoss.core.designsystem.resources.symbol_reply
import com.otusfoss.core.designsystem.resources.symbol_reply_all
import com.otusfoss.core.designsystem.resources.symbol_report
import com.otusfoss.core.designsystem.resources.symbol_search
import com.otusfoss.core.designsystem.resources.symbol_send
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.presentation.ReadLaterChange
import org.jetbrains.compose.resources.DrawableResource

/** The button as the settings page names it, independent of any one message. */
internal val BarButton.label: String
    get() =
        when (this) {
            is BarButton.MoveTo -> "Move to ${path.pathText()}"
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
            MailAction.EditScheduled -> "Edit"
            MailAction.SendNow -> "Send now"
            MailAction.AllActions -> "All actions"
        }

/**
 * The button's name where it shows no state, such as an unavailable or previewed bar button or an
 * alert's button. On the [primary] button, Move to shows only the folder's name.
 */
fun BarButton.buttonLabel(primary: Boolean = false): String =
    when (this) {
        is BarButton.MoveTo -> if (primary) name else "Move to $name"
        MailAction.Read -> MessageFlag.Read.label(on = false)
        MailAction.Flag -> MessageFlag.Flagged.label(on = false)
        else -> label
    }

/** The symbol settings and unavailable slots show, independent of any message. */
internal val BarButton.symbol: DrawableResource
    get() =
        when (this) {
            is BarButton.MoveTo, MailAction.SuggestedMove -> Res.drawable.symbol_folder
            MailAction.Read -> Res.drawable.symbol_mark_email_read
            MailAction.Flag -> Res.drawable.symbol_flag
            MailAction.ReadLater -> Res.drawable.symbol_bookmark
            MailAction.Archive -> Res.drawable.symbol_archive
            MailAction.Trash -> Res.drawable.symbol_delete
            MailAction.Spam -> Res.drawable.symbol_report
            MailAction.NotSpam, MailAction.Restore -> Res.drawable.symbol_inbox
            MailAction.DeleteForever, MailAction.EmptyFolder -> Res.drawable.symbol_delete
            MailAction.Move -> Res.drawable.symbol_drive_file_move
            MailAction.RuleForSender -> Res.drawable.symbol_auto_filter
            MailAction.Reply, MailAction.FollowUp -> Res.drawable.symbol_reply
            MailAction.ReplyAll -> Res.drawable.symbol_reply_all
            MailAction.Forward -> Res.drawable.symbol_forward
            MailAction.NewMail -> Res.drawable.symbol_edit
            MailAction.Search -> Res.drawable.symbol_search
            MailAction.Filters -> Res.drawable.symbol_filter_list
            MailAction.MarkAllRead -> Res.drawable.symbol_mark_email_read
            MailAction.Send, MailAction.SendNow -> Res.drawable.symbol_send
            MailAction.EditScheduled -> Res.drawable.symbol_edit
            MailAction.Attach -> Res.drawable.symbol_attach_file
            MailAction.Discard -> Res.drawable.symbol_delete
            MailAction.AllActions -> Res.drawable.symbol_more_horiz
        }

/** What pressing the button does next: an unread message offers to mark it read, and back. */
internal fun MessageFlag.label(on: Boolean): String =
    when (this) {
        MessageFlag.Read -> if (on) "Mark unread" else "Mark read"
        MessageFlag.Flagged -> if (on) "Unflag" else "Flag"
    }

/** Saving offers Read later; a saved message offers to leave it. */
internal val ReadLaterChange.label: String
    get() = if (saves) "Read later" else "Remove from Read later"
