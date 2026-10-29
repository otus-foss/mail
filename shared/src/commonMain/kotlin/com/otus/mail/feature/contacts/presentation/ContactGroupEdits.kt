package com.otus.mail.feature.contacts.presentation

import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.jscontact.Name
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactPerson

/** A new group named [name] in [book] holding [members]. */
internal fun ContactsState.createGroup(
    name: String,
    book: BookKey,
    members: List<ContactCard> = emptyList(),
): CardKey {
    val group =
        Card(
            kind = CardKind.Group,
            uid = newUid(),
            name = Name(full = name.trim()),
            members = members.mapNotNull { it.card.uid }.associateWith { true }.takeIf { it.isNotEmpty() },
        )
    val created = ContactEdit.Create(newId(), book, group)
    send(created)
    return created.key
}

/** Adds [cards] to [group]. Cards without a uid cannot be members, so they stay out. */
internal fun ContactsState.addToGroup(
    group: ContactCard,
    cards: List<ContactCard>,
) {
    val members = group.card.members.orEmpty() + cards.mapNotNull { it.card.uid }.associateWith { true }
    update(group, group.card.copy(members = members))
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
