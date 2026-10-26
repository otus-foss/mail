package com.otus.mail.feature.contacts.presentation

import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactAddress
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.ContactValue
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.book
import com.otus.mail.feature.contacts.presentation.members
import com.otus.mail.feature.contacts.presentation.person

/**
 * What one contact page shows: the [card] it opened, the [person] that card belongs to, or a
 * group's [members]. The bar, body, and sheets all read it, so they agree on one snapshot.
 */
internal class ContactPage(
    val card: ContactCard,
    val person: ContactPerson?,
    val members: List<ContactPerson>,
    val writable: Boolean,
) {
    /** The person's name, or the card's when it belongs to no person, as for a group. */
    val name: String = person?.name ?: card.displayName

    /** The person's addresses, preferred first; none for a group. */
    val emails: List<ContactValue> = person?.emails.orEmpty()

    /** Every card the page stands for, so Export and Delete reach all of them. */
    val cards: List<ContactCard> = person?.cards ?: listOf(card)

    /** Who Write addresses: each member's first address for a group, else the preferred one. */
    val recipients: List<ContactAddress> =
        if (card.isGroup) {
            members.mapNotNull { p -> p.emails.firstOrNull()?.let { ContactAddress(p.name, it.value) } }
        } else {
            emails.take(1).map { ContactAddress(name, it.value) }
        }
}

/** The page for [card], opened by [key]; it can change only when every book holding it allows it. */
internal fun ContactsState.contactPage(
    key: CardKey,
    card: ContactCard,
): ContactPage {
    val person = person(key)
    val cards = person?.cards ?: listOf(card)
    return ContactPage(
        card = card,
        person = person,
        members = if (card.isGroup) members(card) else emptyList(),
        writable = cards.all { c -> c.books.all { book(it)?.canWrite == true } },
    )
}
