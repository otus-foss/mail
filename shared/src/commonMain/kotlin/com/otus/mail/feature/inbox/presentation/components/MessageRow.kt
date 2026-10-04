package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.designsystem.elements.Avatar
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.MailMessage

@Composable
fun MessageRow(
    message: MailMessage,
    dateFormatter: DateFormatter,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (message.attributes.unread) "Unread" else "Read"
            }.clickable(role = Role.Button, onClick = onClick)
            .padding(InboxTokens.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(InboxTokens.itemGap),
    ) {
        Avatar(
            message.sender.name
                .take(1)
                .uppercase(),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InboxTokens.detailGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(InboxTokens.detailGap)) {
                Text(
                    message.subject.ifBlank { "(No subject)" },
                    Modifier.weight(1f),
                    fontSize = InboxTokens.subjectSize,
                    lineHeight = InboxTokens.subjectLineHeight,
                    fontWeight = if (message.attributes.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(message.received.time, fontSize = InboxTokens.senderSize, color = OtusColors.text2)
            }
            Text(
                message.sender.address,
                fontSize = InboxTokens.senderSize,
                color = OtusColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val attachments = message.attributes.attachments.size
            val metadata =
                listOfNotNull(
                    message.location.mailbox,
                    dateFormatter.formatIsoDate(message.received.date),
                    if (attachments > 0) "$attachments ${if (attachments == 1) "file" else "files"}" else null,
                ).joinToString(" · ")
            Text(
                metadata,
                fontSize = InboxTokens.metadataSize,
                color = OtusColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
