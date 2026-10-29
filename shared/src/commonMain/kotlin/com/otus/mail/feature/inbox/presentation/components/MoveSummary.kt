package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.shared.people.components.NamedAvatar
import com.otus.designsystem.shared.rows.components.SummaryCard
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.read.shortLabel

/**
 * What the move sheet moves: one message's subject, sender, and date, or how many messages and who
 * sent them. One sender shows a full avatar beside the card; several each get a small one before
 * their name.
 */
@Composable
internal fun MoveSummary(
    messages: List<MailMessage>,
    oneSender: Boolean,
) {
    val sender = messages.first().sender
    val today = remember { currentLocalDate() }
    val title =
        messages.singleOrNull()?.subject?.ifBlank { "(No subject)" } ?: "${messages.size} messages"
    if (oneSender) {
        val date = messages.singleOrNull()?.received?.shortLabel(today)
        SummaryCard(
            title,
            listOfNotNull(sender.shownName, date).joinToString(" · "),
            leading = { SenderAvatar(sender) },
        )
    } else {
        val people = messages.map { it.sender }.distinctBy { it.address.lowercase() }
        SummaryCard(title, people.map { NamedAvatar(it.name, it.chipAvatar) })
    }
}
