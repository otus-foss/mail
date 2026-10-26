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
