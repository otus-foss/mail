package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * Files each message into its account's [shortcut] folder, skipping those already there or without
 * one. The offer carries [action]'s name, so the Trash action reads Delete.
 */
internal fun MessageActions.offerFiling(
    messages: List<MailMessage>,
    shortcut: FolderShortcut,
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
    return OfferedAction("Move", MailAction.Move.symbol, leaves = false, asks = true) {
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

/** Moves the messages to [suggestion], the folder the sender's mail usually goes to. */
internal fun MessageActions.offerSuggested(
    messages: List<MailMessage>,
    suggestion: MoveTarget,
): OfferedAction? =
    offerMoves(messages, "Move to ${suggestion.name}", suggestion.name, MailAction.SuggestedMove) { message ->
        moves.targets(message).firstOrNull { it.folderId == suggestion.folderId }
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
