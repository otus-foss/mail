package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.people

internal fun ContactsState.book(key: BookKey): ContactBook? = books.firstOrNull { it.key == key }

internal fun ContactsState.card(key: CardKey): ContactCard? = cards.firstOrNull { it.key == key }

/** [key], or the server's key once a card made on the device has one, so its page stays open. */
internal fun ContactsState.current(key: CardKey): CardKey = sync.renamed[key] ?: key

/** The person [key]'s card belongs to, joined with any card shown as one with it. */
internal fun ContactsState.person(key: CardKey): ContactPerson? =
    people.firstOrNull { person ->
        person.cards.any {
            it.key ==
                key
        }
    }

/** Group cards whose members include [card]. */
internal fun ContactsState.groupsOf(card: ContactCard): List<ContactCard> =
    card.card.uid
        ?.let { uid -> groups.filter { uid in it.members } }
        .orEmpty()

internal fun ContactsState.members(group: ContactCard): List<ContactPerson> {
    val uids = group.members
    return people.filter { person -> person.cards.any { it.card.uid in uids } }
}

/** Cards with [label] on an address or number, compared without case. */
internal fun ContactsState.withLabel(label: String): List<ContactCard> =
    cards.filter { card -> (card.emails + card.phones).any { it.label.custom.equals(label, ignoreCase = true) } }

/** Cards that list [address], compared without case. */
internal fun ContactsState.withAddress(address: String): List<ContactCard> =
    cards.filter { card -> card.emails.any { it.value.equals(address, ignoreCase = true) } }
