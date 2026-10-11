package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The open message's actions, wrapping onto more lines when they do not fit. Filing actions call
 * [onFile] so the message can close first; marks stay open and show their new state.
 */
@Composable
internal fun MessageActionRow(
    message: MailMessage,
    actions: MessageActions,
    onChooseFolder: () -> Unit,
    onFile: (MoveTarget) -> Unit,
    onCreateRule: () -> Unit,
) {
    val targets = actions.moveTargets(message)
    FlowRow {
        MessageFlag.entries.filter { actions.canMark(message, it) }.forEach { flag ->
            val on = message.hasFlag(flag)
            TextButton(onClick = { actions.onMark(message, flag, !on) }) { Text(flag.label(on)) }
        }
        FolderShortcut.entries.forEach { shortcut ->
            actions.shortcutTarget(message, shortcut)?.let { target ->
                TextButton(onClick = { onFile(target) }) { Text(shortcut.label) }
            }
        }
        if (targets.isNotEmpty()) TextButton(onClick = onChooseFolder) { Text("Move") }
        if (actions.canCreateRule(message)) TextButton(onClick = onCreateRule) { Text("Rule for sender") }
    }
}

/** What pressing the button does next: an unread message offers to mark it read, and back. */
private fun MessageFlag.label(on: Boolean): String =
    when (this) {
        MessageFlag.Read -> if (on) "Mark unread" else "Mark read"
        MessageFlag.Flagged -> if (on) "Unflag" else "Flag"
    }

private val FolderShortcut.label: String
    get() =
        when (this) {
            FolderShortcut.Archive -> "Archive"
            FolderShortcut.Trash -> "Trash"
        }
