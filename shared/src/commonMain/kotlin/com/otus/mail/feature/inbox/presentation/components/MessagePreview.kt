package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceDialog
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * A read-only message. With [actions], it can be marked, archived, trashed, moved to a folder, or
 * start a rule for its sender. The actions stay in view while a long body scrolls.
 */
@Composable
fun MessagePreview(
    message: MailMessage,
    actions: MessageActions? = null,
    onDismiss: () -> Unit,
) {
    var moving by rememberSaveable { mutableStateOf(false) }
    val file: (MoveTarget) -> Unit = { target ->
        onDismiss()
        actions?.onMove(message, target)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(message.subject.ifBlank { "(No subject)" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InboxTokens.itemGap)) {
                Text("${message.sender.name} <${message.sender.address}>", color = OtusColors.text2)
                actions?.let {
                    MessageActionRow(message, it, onChooseFolder = { moving = true }, onFile = file) {
                        onDismiss()
                        it.onCreateRule(message)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(InboxTokens.itemGap),
                ) {
                    Text(message.body)
                    if (message.attributes.attachments.isNotEmpty()) {
                        Text("Attachments: ${message.attributes.attachments.joinToString()}", color = OtusColors.text2)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (moving && actions != null) {
        ChoiceDialog<MoveTarget?>(
            "Move to",
            actions.moveTargets(message),
            selected = null,
            label = { it?.label.orEmpty() },
            onSelect = { target -> target?.let(file) },
            onDismiss = { moving = false },
        )
    }
}
