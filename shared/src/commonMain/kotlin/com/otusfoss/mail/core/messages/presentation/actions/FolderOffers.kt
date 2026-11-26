package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.MoveTarget

/** Opens the Move to sheet for some messages, offering the targets every one of them can move to. */
internal typealias ChooseFolder = (messages: List<MailMessage>, targets: List<MoveTarget>) -> Unit

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
    val target = { message: MailMessage -> moves.targets(message).at(button.key) }
    val name = messages.firstNotNullOfOrNull(target)?.name ?: return null
    return offerMoves(messages, "Move to $name", name, button, target)
}

/**
 * Adds each message to the folder at [button]'s path in its own account and keeps it where it is.
 * Once every message has the label, the button takes it off instead. With [addOnly], as a chain
 * runs it, it only ever adds.
 */
internal fun MessageActions.offerLabel(
    messages: List<MailMessage>,
    button: BarButton.LabelAs,
    addOnly: Boolean = false,
): OfferedAction? {
    val folders = messages.mapNotNull { message -> moves.listed(message).at(button.key)?.let { message to it } }
    val changes = folders.mapNotNull { (message, folder) -> moves.label(message, folder) }
    val adding = changes.filter { it.adds }
    val chosen = if (addOnly) adding else adding.ifEmpty { changes }
    if (chosen.isEmpty()) return null
    val name = folders.first().second.name
    val label = if (chosen.first().adds) "Label as $name" else "Remove label $name"
    return OfferedAction(label, button.symbol, chosen.any { it.leaves }, primaryLabel = name) {
        chosen.forEach { it.apply() }
    }
}

/** The folder at the shared folder path [key], which is in lower case. */
private fun List<MoveTarget>.at(key: List<String>): MoveTarget? =
    firstOrNull { folder -> folder.path?.map { it.lowercase() } == key }

/** Moves messages of [folder]'s account to [folder], such as the one the sender's mail usually goes to. */
internal fun MessageActions.offerFolder(
    messages: List<MailMessage>,
    folder: MoveTarget,
): OfferedAction? {
    val accountId = folder.accountId ?: messages.firstOrNull()?.location?.accountId ?: return null
    return offerMoves(messages, "Move to ${folder.name}", folder.name, MailAction.SuggestedMove) { message ->
        if (message.location.accountId != accountId) return@offerMoves null
        moves.targets(message).firstOrNull { it.folderId == folder.folderId }
    }
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
