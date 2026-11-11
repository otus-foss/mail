package com.otusfoss.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.people.components.NamedAvatar
import com.otusfoss.core.designsystem.shared.rows.components.SummaryCard
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.shortLabel

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
    val today = mailToday()
    val title =
        messages.singleOrNull()?.subject?.ifBlank { "(No subject)" } ?: "${messages.size} messages"
    if (oneSender) {
        val date = messages.singleOrNull()?.received?.let { LocalDateFormatter.current.shortLabel(it, today) }
        SummaryCard(
            title,
            listOfNotNull(sender.shownName, date).joinToString(" · "),
            leading = { SenderAvatar(sender) },
        )
    } else {
        val people = messages.map { it.sender }.distinctBy { EmailAddressKey(it.address) }
        SummaryCard(title, people.map { NamedAvatar(it.name, it.chipAvatar) })
    }
}
