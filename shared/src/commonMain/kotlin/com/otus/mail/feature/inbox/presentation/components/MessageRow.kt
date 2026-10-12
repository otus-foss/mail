package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.components.ContentRow
import com.otus.designsystem.components.ContentRowText
import com.otus.designsystem.components.RowLabel
import com.otus.designsystem.components.RowSelection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.receivedLabel

/**
 * One message in a list: subject, sender, and when it arrived. The last line shows only what the
 * page doesn't already say: the message's other folders as labels, its account when the list
 * mixes accounts ([showAccount]), and its attachments. Without any of these, it shows the start
 * of the message. A selected row shows a check instead of the sender's avatar; a null [selection]
 * means the list is not selecting.
 */
@Composable
fun MessageRow(
    message: MailMessage,
    dateFormatter: DateFormatter,
    showAccount: Boolean,
    selection: RowSelection? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val labels = message.labels
    val attachments = message.attributes.attachments.size
    val details =
        listOfNotNull(
            message.location.account.takeIf { showAccount },
            "$attachments ${if (attachments == 1) "file" else "files"}".takeIf { attachments > 0 },
        ).joinToString(" · ")
    ContentRow(
        text =
            ContentRowText(
                message.subject.ifBlank { "(No subject)" },
                message.sender.name.ifBlank { message.sender.address },
                dateFormatter.receivedLabel(message.received, currentLocalDate()),
                if (labels.isEmpty() && details.isEmpty()) message.body.preview() else details,
                labels.map { label ->
                    RowLabel(label.name, Res.drawable.symbol_bookmark.takeIf { label.readLater }, label.readLater)
                },
            ),
        emphasized = message.attributes.unread,
        stateLabel = if (message.attributes.unread) "Unread" else "Read",
        onClick = onClick,
        selection = selection,
        onLongClick = onLongClick,
        leading = { SenderAvatar(message.sender) },
    )
}

/** The start of the body on one line. */
private fun String.preview(): String = take(PREVIEW_LENGTH).replace(whitespace, " ").trim()

private const val PREVIEW_LENGTH = 200
private val whitespace = Regex("\\s+")
