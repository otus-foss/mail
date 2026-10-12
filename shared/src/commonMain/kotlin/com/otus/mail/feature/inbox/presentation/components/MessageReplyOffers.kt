package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.original

/**
 * Reply, Reply all, Forward, and New mail for an open [message]; null for any other action. Each
 * starts a draft through [onCompose]. New mail sends from the message's account.
 */
internal fun replyOffer(
    message: MailMessage,
    action: MailAction,
    onCompose: (ComposeRequest) -> Unit,
): OfferedAction? {
    val request =
        when (action) {
            MailAction.Reply -> ComposeRequest.Reply(message.original(), all = false)
            MailAction.ReplyAll -> ComposeRequest.Reply(message.original(), all = true)
            MailAction.Forward -> ComposeRequest.Forward(message.original())
            MailAction.NewMail -> ComposeRequest.New(message.location.accountId)
            else -> return null
        }
    return plainOffer(action) { onCompose(request) }
}

/** Who [primary] answers, shown beside it: the sender for Reply, and everyone for Reply all. */
internal fun replyContext(
    message: MailMessage,
    primary: MailAction,
): BarContext? {
    val sender = message.sender
    val name = sender.name.ifBlank { sender.address }
    val people = message.replyAllCount()
    return when {
        primary == MailAction.ReplyAll && people > 1 -> {
            BarContext("$people people", BarToken.People(sender.chipAvatar, people), "to $people people")
        }

        primary == MailAction.Reply || primary == MailAction.ReplyAll -> {
            BarContext(name, BarToken.Person(sender.chipAvatar), "to $name")
        }

        else -> {
            null
        }
    }
}

/** The distinct people a reply to all reaches: the sender, To, and Cc, without the account itself. */
private fun MailMessage.replyAllCount(): Int {
    val own = location.accountAddress.lowercase()
    return (listOf(sender) + to + cc)
        .map { it.address.lowercase() }
        .filter { it.isNotBlank() && it != own }
        .distinct()
        .size
}
