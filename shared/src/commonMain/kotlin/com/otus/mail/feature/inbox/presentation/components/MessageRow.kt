package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ContentRow
import com.otus.designsystem.components.ContentRowText
import com.otus.mail.feature.inbox.domain.MailMessage

@Composable
fun MessageRow(
    message: MailMessage,
    dateFormatter: DateFormatter,
    onClick: () -> Unit,
) {
    val attachments = message.attributes.attachments.size
    val metadata =
        listOfNotNull(
            message.location.mailbox,
            dateFormatter.formatIsoDate(message.received.date),
            if (attachments > 0) "$attachments ${if (attachments == 1) "file" else "files"}" else null,
        ).joinToString(" · ")
    ContentRow(
        text =
            ContentRowText(
                message.subject.ifBlank { "(No subject)" },
                message.sender.address,
                message.received.time,
                metadata,
            ),
        emphasized = message.attributes.unread,
        stateLabel = if (message.attributes.unread) "Unread" else "Read",
        onClick = onClick,
        leading = { SenderAvatar(message.sender) },
    )
}
