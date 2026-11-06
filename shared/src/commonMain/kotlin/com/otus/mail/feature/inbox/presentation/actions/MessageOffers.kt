package com.otus.mail.feature.inbox.presentation.actions

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMarks
import com.otus.mail.feature.inbox.presentation.MoveTarget

/** Opens the Move to sheet for some messages, offering the targets every one of them can move to. */
internal typealias ChooseFolder = (messages: List<MailMessage>, targets: List<MoveTarget>) -> Unit

/** Turns [flag] on for all [messages] while any lacks it, and off otherwise. */
internal fun MessageMarks.offer(
    messages: List<MailMessage>,
    flag: MessageFlag,
): OfferedAction? {
    val markable = messages.filter { can(it, flag) }
    if (markable.isEmpty()) return null
    val on = markable.all { it.hasFlag(flag) }
    val symbol =
        when {
            flag == MessageFlag.Flagged -> Res.drawable.symbol_flag
            on -> Res.drawable.symbol_mark_email_unread
            else -> Res.drawable.symbol_mark_email_read
        }
    return OfferedAction(flag.label(on), symbol, leaves = false) { set(markable, flag, !on) }
}

/** Saves the messages not saved yet; once all are saved, removes them. */
internal fun MessageActions.offerReadLater(messages: List<MailMessage>): OfferedAction? {
    val changes = messages.mapNotNull(readLater)
    val saving = changes.filter { it.saves }
    val chosen = saving.ifEmpty { changes }.ifEmpty { return null }
    return OfferedAction(chosen.first().label, Res.drawable.symbol_bookmark, chosen.any { it.leaves }) {
        chosen.forEach { it.apply() }
    }
}

internal fun MessageActions.offerRule(messages: List<MailMessage>): OfferedAction? =
    messages.singleOrNull()?.let(ruleForSender)?.let { start ->
        OfferedAction(MailAction.RuleForSender.label, MailAction.RuleForSender.symbol, leaves = true, run = start)
    }

/**
 * Files each message into its account's [shortcut] folder, skipping those already there or without
 * one. The offer carries [action]'s name, so the Trash action reads Delete.
 */
internal fun MessageActions.offerFiling(
    messages: List<MailMessage>,
    shortcut: MailboxRole,
    action: BarButton,
): OfferedAction? = offerMoves(messages, action.label, action.label, action) { shortcutTarget(it, shortcut) }

/** Opens the Move to sheet with the folders every message can move to, all from one account. */
internal fun MessageActions.offerMove(
    messages: List<MailMessage>,
    chooseFolder: ChooseFolder,
): OfferedAction? {
    val oneAccount = messages.distinctBy { it.location.accountId }.size == 1
    val targets =
        if (oneAccount) {
            messages
                .map { moves.targets(it) }
                .reduce { common, next -> common.filter { target -> next.any { it.folderId == target.folderId } } }
        } else {
            emptyList()
        }
    if (targets.isEmpty()) return null
    return OfferedAction(MailAction.Move.label, MailAction.Move.symbol, leaves = false, asks = true) {
        chooseFolder(messages, targets)
    }
}

/** Moves each message to the folder at [button]'s path in its own account, where it has one. */
internal fun MessageActions.offerMoveTo(
    messages: List<MailMessage>,
    button: BarButton.MoveTo,
): OfferedAction? {
    val target = { message: MailMessage ->
        moves.targets(message).firstOrNull { folder -> folder.path?.map { it.lowercase() } == button.key }
    }
    val name = messages.firstNotNullOfOrNull(target)?.name ?: return null
    return offerMoves(messages, "Move to $name", name, button, target)
}

/** Moves the messages to [folder], such as the one the sender's mail usually goes to. */
internal fun MessageActions.offerFolder(
    messages: List<MailMessage>,
    folder: MoveTarget,
): OfferedAction? =
    offerMoves(messages, "Move to ${folder.name}", folder.name, MailAction.SuggestedMove) { message ->
        moves.targets(message).firstOrNull { it.folderId == folder.folderId }
    }

/** Moves each message to its [target]; null when none has one. The main button shows [primaryLabel]. */
private fun MessageActions.offerMoves(
    messages: List<MailMessage>,
    label: String,
    primaryLabel: String,
    button: BarButton,
    target: (MailMessage) -> MoveTarget?,
): OfferedAction? {
    val moving = messages.mapNotNull { message -> target(message)?.let { message to it } }
    if (moving.isEmpty()) return null
    return OfferedAction(label, button.symbol, leaves = true, primaryLabel = primaryLabel) {
        moving.forEach { (message, folder) -> moves.onMove(message, folder) }
    }
}
