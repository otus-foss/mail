package com.otus.mail.feature.read.domain

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.isOwn

/** How someone took part in a conversation, from the most to the least direct. */
enum class ParticipantRole { Sender, Recipient, Copied }

/** Someone a conversation names other than the user, with the most direct [role] they had. */
data class Participant(
    val person: MailSender,
    val role: ParticipantRole,
)

/**
 * Everyone [messages] name besides the user: senders, reply-to addresses, and To and Cc
 * recipients, each once however many messages name them. A person keeps the most direct role
 * they had and the first name any message gives them; senders come first, then recipients.
 */
fun participants(
    messages: List<MailMessage>,
    own: OwnAddresses,
): List<Participant> =
    messages
        .flatMap { message ->
            (
                (listOf(message.sender) + message.replyTo).map { it to ParticipantRole.Sender } +
                    message.to.map { it to ParticipantRole.Recipient } +
                    message.cc.map { it to ParticipantRole.Copied }
            ).filterNot { (person, _) -> person.address.isBlank() || message.isOwn(own, person) }
        }.groupBy { (person, _) -> EmailAddressKey(person.address) }
        .map { (_, named) ->
            val person = named.firstOrNull { it.first.name.isNotBlank() }?.first ?: named.first().first
            Participant(person, named.minOf { it.second })
        }.sortedBy { it.role }
