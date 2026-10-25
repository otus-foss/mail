package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarPhotoPicker
import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.jscontact.Media
import com.otus.jscontact.MediaKind
import com.otus.jscontact.Name
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactFile
import com.otus.mail.feature.contacts.domain.ContactFiles
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.domain.ImportMode
import com.otus.mail.feature.contacts.domain.ImportPlan
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.applyEdits
import com.otus.mail.feature.contacts.domain.duplicates
import com.otus.mail.feature.contacts.domain.people
import com.otus.mail.feature.contacts.domain.withPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    return ContactEdit.Create(newId(), book, group).also(::send).key
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
