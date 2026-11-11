package com.otusfoss.mail.core.messages.presentation.components

import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.actionbar.components.BarToken
import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.shownName

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
        0 -> null
        1 -> personContext(people.single())
        else -> groupContext(people)
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
    val own = EmailAddressKey(location.accountAddress)
    return people
        .filter { it.address.isNotBlank() && EmailAddressKey(it.address) != own }
        .distinctBy { EmailAddressKey(it.address) }
}
