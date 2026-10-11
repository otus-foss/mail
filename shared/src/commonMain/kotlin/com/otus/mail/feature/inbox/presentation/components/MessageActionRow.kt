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

/**
 * The open message's actions, wrapping onto more lines when they do not fit. Actions that take the
 * message away go through [onLeave], so the message closes first; marks and a Read later change
 * that keeps the message in place stay open and show their new state.
 */
@Composable
internal fun MessageActionRow(
    message: MailMessage,
    actions: MessageActions,
    onChooseFolder: () -> Unit,
    onLeave: (run: () -> Unit) -> Unit,
) {
    FlowRow {
        MessageFlag.entries.filter { actions.canMark(message, it) }.forEach { flag ->
            val on = message.hasFlag(flag)
            TextButton(onClick = { actions.onMark(message, flag, !on) }) { Text(flag.label(on)) }
        }
        actions.readLater(message)?.let { change ->
            TextButton(
                onClick = { if (change.leaves) onLeave(change.apply) else change.apply() },
            ) { Text(change.label) }
        }
        FolderShortcut.entries.forEach { shortcut ->
            actions.shortcutTarget(message, shortcut)?.let { target ->
                TextButton(onClick = { onLeave { actions.onMove(message, target) } }) { Text(shortcut.label) }
            }
        }
        if (actions.moveTargets(message).isNotEmpty()) TextButton(onClick = onChooseFolder) { Text("Move") }
        actions.ruleForSender(message)?.let { start ->
            TextButton(onClick = { onLeave(start) }) { Text("Rule for sender") }
        }
    }
}
