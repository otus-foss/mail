package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The user's own folder where most of the sender's recent mail is, read from the server, or null
 * until it loads. Only [messages] from one sender have one, and only while [wanted], so a bar
 * without Suggested move asks the server nothing.
 */
@Composable
internal fun rememberSuggestedMove(
    messages: List<MailMessage>,
    actions: MessageActions?,
    wanted: Boolean = true,
): MoveTarget? =
    produceState<MoveTarget?>(null, messages.map { it.id }, wanted && actions != null) {
        // One sender also means at least one message.
        val oneSender = messages.distinctBy { EmailAddressKey(it.sender.address) }.size == 1
        value = if (wanted && oneSender && actions != null) actions.moves.suggested(messages.first()) else null
    }.value
