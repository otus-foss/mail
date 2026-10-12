package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.NamedAvatar
import com.otus.designsystem.components.SummaryCard
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender

/**
 * What the move sheet moves: one message's subject and sender, or how many messages and who sent
 * them. One sender shows a full avatar beside the card; several each get a small one before their
 * name.
 */
@Composable
internal fun MoveSummary(
    messages: List<MailMessage>,
    oneSender: Boolean,
) {
    val sender = messages.first().sender
    val title =
        messages.singleOrNull()?.subject?.ifBlank { "(No subject)" } ?: "${messages.size} messages"
    if (oneSender) {
        SummaryCard(title, sender.line(), leading = { SenderAvatar(sender) })
    } else {
        val people = messages.map { it.sender }.distinctBy { it.address.lowercase() }
        SummaryCard(title, people.map { NamedAvatar(it.name, it.chipAvatar) })
    }
}

/** The name and address, or the address alone when the sender has no other name. */
private fun MailSender.line(): String = if (name == address) address else "$name · $address"
