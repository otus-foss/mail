package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.MailMessage

@Composable
fun MessagePreview(
    message: MailMessage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(message.subject.ifBlank { "(No subject)" }) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InboxTokens.itemGap),
            ) {
                Text("${message.sender.name} <${message.sender.address}>", color = OtusColors.text2)
                Text(message.body)
                if (message.attributes.attachments.isNotEmpty()) {
                    Text("Attachments: ${message.attributes.attachments.joinToString()}", color = OtusColors.text2)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
