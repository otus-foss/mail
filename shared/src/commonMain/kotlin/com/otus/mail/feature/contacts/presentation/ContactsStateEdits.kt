package com.otus.mail.feature.contacts.presentation

import com.otus.jscontact.Media
import com.otus.jscontact.Name
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.domain.withPhoto

/** Cards with [label] on an address or number, compared without case. */
internal fun ContactsState.withLabel(label: String): List<ContactCard> =
    cards.filter { card -> (card.emails + card.phones).any { it.label.custom.equals(label, ignoreCase = true) } }

/**
 * Removes the custom [label] from every card the user may change; those values keep no label.
 * Cards in read-only books keep it, so it may still be offered afterwards.
 */
internal fun ContactsState.deleteLabel(label: String) {
    withLabel(label)
        .filter { card -> card.books.all { book(it)?.canWrite == true } }
        .forEach { card ->
            val draft = ContactDraft.of(card)
            val cleared = { values: List<DraftValue> ->
                values.map {
                    if (it.label.custom.equals(
                            label,
                            ignoreCase = true,
                        )
                    ) {
                        it.copy(label = ContactLabel.Other)
                    } else {
                        it
                    }
                }
            }
            save(
                draft.copy(emails = cleared(draft.emails), phones = cleared(draft.phones)),
                card,
                card.books.first(),
            )
        }
}

/** Cards that list [address], compared without case. */
internal fun ContactsState.withAddress(address: String): List<ContactCard> =
    cards.filter { card -> card.emails.any { it.value.equals(address, ignoreCase = true) } }

/** Adds [address] to [card], keeping its other addresses and which one is preferred. */
internal fun ContactsState.addAddress(
    card: ContactCard,
    address: String,
) {
    val draft = ContactDraft.of(card)
    save(draft.copy(emails = draft.emails + DraftValue(value = address)), card, card.books.first())
}

/** Takes [person]'s cards out of [group]; the cards themselves stay. */
internal fun ContactsState.removeFromGroup(
    group: ContactCard,
    person: ContactPerson,
) {
    val uids = person.cards.mapNotNull { it.card.uid }.toSet()
    val members =
        group.card.members
            .orEmpty()
            .filterKeys { it !in uids }
    update(group, group.card.copy(members = members.takeIf { it.isNotEmpty() }))
}

/** Gives [group] a new [name]. */
internal fun ContactsState.renameGroup(
    group: ContactCard,
    name: String,
) = update(group, group.card.copy(name = Name(full = name.trim())))

/** Gives [card], such as a group, [photo], or takes its photo away when null. */
internal fun ContactsState.setPhoto(
    card: ContactCard,
    photo: Media?,
) = update(card, card.card.withPhoto(photo))
