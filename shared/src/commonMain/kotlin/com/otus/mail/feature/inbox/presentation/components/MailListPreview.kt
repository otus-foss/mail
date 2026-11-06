package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.messages.domain.MailAttributes
import com.otus.mail.core.messages.domain.MailConversation
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.RowFolder
import com.otus.mail.core.messages.domain.ThreadEmail
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.feature.inbox.presentation.RowLook
import com.otus.mail.feature.inbox.presentation.conversationRows

/** One sample conversation drawn with [layout], so the Mail list settings show their effect at once. */
@Composable
fun MailListPreview(
    layout: MailListLayout,
    dateFormatter: DateFormatter,
) {
    val today = currentLocalDate()
    val message = remember(today) { sampleConversation(ReceivedAt(today.toString(), "09:14")) }
    val model =
        remember(layout, today) {
            conversationRows(listOf(message), RowLook(layout), dateFormatter, today).getValue(message.id)
        }
    ConversationRow(message, model, Modifier.clearAndSetSemantics {}) {}
}

private fun sampleConversation(received: ReceivedAt): MailMessage {
    val grace = MailSender("Grace Hopper", "grace@example.com")
    return MailMessage(
        id = "preview",
        sender = grace,
        subject = "Re: Q4 launch plan",
        body = "",
        received = received,
        location = MailLocation("Inbox", "Work"),
        attributes = MailAttributes(unread = true, hasAttachment = true),
        labels = listOf(RowFolder("clients", "Clients", color = FolderColor.Blue.name)),
        preview = "Let's lock it in on Thursday at 10. Two warm-up talks, then the demo.",
        conversation =
            MailConversation(
                threadId = "preview",
                messageCount = 5,
                last = ThreadEmail(grace, emptyList(), received),
                senders = listOf(grace) + (1..4).map { MailSender("Guest $it", "guest$it@example.com") },
                answered = false,
                members = emptyList(),
            ),
    )
}
