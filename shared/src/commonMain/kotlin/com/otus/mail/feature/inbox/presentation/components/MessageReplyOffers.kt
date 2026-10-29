package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.original

/**
 * Reply, Reply all, Forward, and New mail for an open [message], and Follow up when its folder
 * [role] is Sent; null for any other action. Each starts a draft through [onCompose]. New mail
 * sends from the message's account.
 */
internal fun replyOffer(
    message: MailMessage,
    action: BarButton,
    role: FolderRole?,
    onCompose: (ComposeRequest) -> Unit,
): OfferedAction? {
    val request =
        when {
            action == MailAction.Reply -> ComposeRequest.Reply(message.original(), all = false)
            action == MailAction.ReplyAll -> ComposeRequest.Reply(message.original(), all = true)
            action == MailAction.FollowUp && role == FolderRole.Sent -> ComposeRequest.FollowUp(message.original())
            action == MailAction.Forward -> ComposeRequest.Forward(message.original())
            action == MailAction.NewMail -> ComposeRequest.New(message.location.accountId)
            else -> return null
        }
    return plainOffer(action) { onCompose(request) }
}

/**
 * Who [primary] writes to, shown beside it: the sender for Reply, everyone for Reply all, and the
 * message's recipients for Follow up.
 */
internal fun replyContext(
    message: MailMessage,
    primary: BarButton,
): BarContext? {
    val everyone = message.others(listOf(message.sender) + message.to + message.cc)
    return when {
        primary == MailAction.FollowUp -> peopleContext(message.others(message.to + message.cc))
        primary == MailAction.ReplyAll && everyone.size > 1 -> peopleContext(everyone)
        primary == MailAction.Reply || primary == MailAction.ReplyAll -> personContext(message.sender)
        else -> null
    }
}

private fun peopleContext(people: List<MailSender>): BarContext? =
    when (people.size) {
        0 -> {
            null
        }

        1 -> {
            personContext(people.single())
        }

        else -> {
            groupContext(people)
        }
    }

/** Several [people], as the first two avatars and how many there are, under [lead] when given. */
internal fun groupContext(
    people: List<MailSender>,
    lead: String? = null,
): BarContext {
    val count = "${people.size} people"
    val avatars = BarToken.People(people[0].chipAvatar, people.size, people[1].chipAvatar)
    return BarContext(count, avatars, "to $count", lead = lead)
}

private fun personContext(person: MailSender): BarContext {
    val name = person.shownName
    return BarContext(name, BarToken.Person(person.chipAvatar), "to $name")
}

/** [people], each address once, without the account itself. */
private fun MailMessage.others(people: List<MailSender>): List<MailSender> {
    val own = location.accountAddress.lowercase()
    return people
        .filter { it.address.isNotBlank() && it.address.lowercase() != own }
        .distinctBy { it.address.lowercase() }
}
