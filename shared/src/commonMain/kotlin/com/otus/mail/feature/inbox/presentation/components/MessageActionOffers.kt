package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_auto_filter
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.resources.symbol_send
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget
import org.jetbrains.compose.resources.DrawableResource

/** Opens the Move to sheet for some messages, offering the targets every one of them can move to. */
internal typealias ChooseFolder = (messages: List<MailMessage>, targets: List<MoveTarget>) -> Unit

/**
 * [action] for [messages], one or a selection; null when none of them can take it. A mark turns
 * on for all of them while any lacks it, and off otherwise. Moving needs one account, because
 * folders belong to an account. Suggested move goes to [suggestion], when there is one.
 */
internal fun MessageActions.offer(
    messages: List<MailMessage>,
    action: BarButton,
    chooseFolder: ChooseFolder,
    suggestion: MoveTarget? = null,
): OfferedAction? =
    when (action) {
        is BarButton.MoveTo -> offerMoveTo(messages, action)

        MailAction.Read -> offerMark(messages, MessageFlag.Read)

        MailAction.Flag -> offerMark(messages, MessageFlag.Flagged)

        MailAction.ReadLater -> offerReadLater(messages)

        MailAction.Archive -> offerFiling(messages, FolderShortcut.Archive, action)

        MailAction.Trash -> offerFiling(messages, FolderShortcut.Trash, action)

        MailAction.Spam -> offerFiling(messages, FolderShortcut.Spam, action)

        MailAction.NotSpam, MailAction.Restore, MailAction.DeleteForever -> offerCleanup(messages, action)

        MailAction.Move -> offerMove(messages, chooseFolder)

        MailAction.SuggestedMove -> suggestion?.let { offerSuggested(messages, it) }

        MailAction.RuleForSender -> offerRule(messages)

        // Screen and draft actions are offered by their screens, not for messages.
        else -> null
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

            // Stand-ins until Meta bundles symbol_reply, symbol_reply_all, and symbol_forward.
            MailAction.Reply, MailAction.ReplyAll, MailAction.Forward, MailAction.FollowUp -> Res.drawable.symbol_send

            MailAction.NewMail -> Res.drawable.symbol_edit

            MailAction.Search -> Res.drawable.symbol_search

            MailAction.Filters -> Res.drawable.symbol_filter_list

            MailAction.MarkAllRead -> Res.drawable.symbol_mark_email_read

            MailAction.Send -> Res.drawable.symbol_send

            MailAction.Attach -> Res.drawable.symbol_attach_file

            MailAction.Discard -> Res.drawable.symbol_close
        }

private fun MessageActions.offerMark(
    messages: List<MailMessage>,
    flag: MessageFlag,
): OfferedAction? {
    val markable = messages.filter { canMark(it, flag) }
    if (markable.isEmpty()) return null
    val on = markable.all { it.hasFlag(flag) }
    val symbol =
        when {
            flag == MessageFlag.Flagged -> Res.drawable.symbol_flag
            on -> Res.drawable.symbol_mark_email_unread
            else -> Res.drawable.symbol_mark_email_read
        }
    return OfferedAction(flag.label(on), symbol, leaves = false) { markable.forEach { onMark(it, flag, !on) } }
}

/** Saves the messages not saved yet; once all are saved, removes them. */
private fun MessageActions.offerReadLater(messages: List<MailMessage>): OfferedAction? {
    val changes = messages.mapNotNull(readLater)
    val saving = changes.filter { it.saves }
    val chosen = saving.ifEmpty { changes }.ifEmpty { return null }
    return OfferedAction(chosen.first().label, Res.drawable.symbol_bookmark, chosen.any { it.leaves }) {
        chosen.forEach { it.apply() }
    }
}

private fun MessageActions.offerRule(messages: List<MailMessage>): OfferedAction? =
    messages.singleOrNull()?.let(ruleForSender)?.let { start ->
        OfferedAction("Rule for sender", MailAction.RuleForSender.symbol, leaves = true, run = start)
    }
