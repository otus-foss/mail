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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Every login's contacts and the changes made on the device. A change shows at once and is sent in
 * the background; offline it waits, and it survives a restart through [sync]'s store. One per app.
 */
internal class ContactsState(
    private val repository: ContactRepository,
    private val sync: ContactSync,
    private val scope: CoroutineScope,
    private val files: ContactFiles? = null,
    private val photos: AvatarPhotoPicker? = null,
    private val frequent: FrequentContacts? = null,
    private val avatars: ContactAvatarUpdates? = null,
) {
    var snapshot by mutableStateOf<ContactsSnapshot?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Something to tell the user once, such as a failed load. */
    var notice by mutableStateOf<String?>(null)

    /** A vCard file picked for import, waiting for the user to confirm it. */
    var importing by mutableStateOf<ContactFile?>(null)

    val canTransfer: Boolean get() = files != null

    val canPickPhoto: Boolean get() = photos != null

    /**
     * A photo the user picks, cropped and small, as an inline `data:` URI: Stalwart refuses a
     * `blobId` in `media`, and an inline photo shows in every contact app. Null when cancelled.
     */
    suspend fun pickPhoto(): Media? =
        photos?.pick()?.let {
            Media(
                MediaKind.Photo,
                uri = "data:${it.mediaType};base64,${Base64.encode(it.image.bytes)}",
                mediaType = it.mediaType,
            )
        }

    /** What the next new-contact editor starts from, such as a message's sender; taken once. */
    var prefill by mutableStateOf<ContactDraft?>(null)

    /** Every custom label on the cards' addresses and numbers, so the editor can offer them again. */
    val customLabels: List<String> by derivedStateOf {
        cards
            .flatMap { card -> card.emails + card.phones }
            .mapNotNull { it.label.custom?.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    val books: List<ContactBook> get() = snapshot?.books.orEmpty()
    val writableBooks: List<ContactBook> by derivedStateOf { books.filter { it.canWrite } }

    /** The default book to save new cards in: the first login's default, else its first writable one. */
    val defaultBook: ContactBook? by derivedStateOf {
        writableBooks.firstOrNull { it.isDefault }
            ?: writableBooks.firstOrNull()
    }

    val cards: List<ContactCard> by derivedStateOf { applyEdits(snapshot?.cards.orEmpty(), sync.edits, sync.refused) }
    val people: List<ContactPerson> by derivedStateOf { people(cards, sync.links) }

    /** The [FREQUENT_SHOWN] people the user deals with most, by their best-scoring address. */
    val mostFrequent: List<ContactPerson> by derivedStateOf {
        val scores = frequent?.scores.orEmpty()
        people
            .map { person -> person to (person.emails.maxOfOrNull { scores[it.value.lowercase()] ?: 0.0 } ?: 0.0) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .take(FREQUENT_SHOWN)
            .map { it.first }
    }
    val groups: List<ContactCard> by derivedStateOf {
        cards.filter { it.isGroup }.sortedBy { it.displayName.lowercase() }
    }
    val duplicates by derivedStateOf { duplicates(cards, sync.links, sync.dismissed) }

    /** Whether some change has not reached the server yet. */
    val waiting: Int get() = sync.edits.count { it.id !in sync.refused }

    fun book(key: BookKey): ContactBook? = books.firstOrNull { it.key == key }

    fun card(key: CardKey): ContactCard? = cards.firstOrNull { it.key == key }

    /** The person [key]'s card belongs to, joined with any card shown as one with it. */
    fun person(key: CardKey): ContactPerson? = people.firstOrNull { person -> person.cards.any { it.key == key } }

    /** Group cards whose members include [card]. */
    fun groupsOf(card: ContactCard): List<ContactCard> =
        card.card.uid
            ?.let { uid -> groups.filter { uid in it.members } }
            .orEmpty()

    fun members(group: ContactCard): List<ContactPerson> {
        val uids = group.members
        return people.filter { person -> person.cards.any { it.card.uid in uids } }
    }

    fun refresh() {
        frequent?.refresh()
        if (loading) return
        loading = true
        scope.launch {
            try {
                val loaded = repository.load()
                snapshot = loaded
                if (loaded.failedLogins.isNotEmpty() && loaded.cards.isEmpty()) notice = "Couldn’t load your contacts."
                sync.flush(this@ContactsState::sent)
            } finally {
                loading = false
            }
        }
    }

    /**
     * Saves [draft] over [existing], or as a new card in [book]. Returns the card's key, which for a
     * new card is a device key until the server names it.
     */
    fun save(
        draft: ContactDraft,
        existing: ContactCard?,
        book: BookKey,
    ): CardKey {
        val edit =
            if (existing == null) {
                ContactEdit.Create(newId(), book, draft.applyTo(Card(kind = CardKind.Individual, uid = newUid())))
            } else {
                val moved = book !in existing.books
                val after = draft.applyTo(existing.card)
                ContactEdit.Update(
                    newId(),
                    existing.key,
                    existing.card,
                    if (moved) after.copy(addressBookIds = mapOf(book.bookId to true)) else after,
                )
            }
        sync.add(edit, ::sent)
        return edit.key
    }

    fun delete(keys: Collection<CardKey>) {
        keys.forEach { key -> sync.add(ContactEdit.Delete(newId(), key), ::sent) }
        sync.unlink(keys)
    }

    /** Saves [plan]'s merged card, then removes the others if the plan says so. */
    fun merge(plan: MergePlan): CardKey {
        val merged = plan.merged()
        val target = plan.target
        val key =
            if (target == null) {
                ContactEdit.Create(newId(), plan.book, merged.copy(id = null)).also { sync.add(it, ::sent) }.key
            } else {
                sync.add(ContactEdit.Update(newId(), target.key, target.card, merged), ::sent)
                target.key
            }
        delete(plan.removed.map { it.key })
        sync.dismiss(plan.cards.map { it.key }.toSet())
        return key
    }

    /** Shows [keys] as one person without changing the cards. */
    fun showAsOne(keys: Set<CardKey>) = sync.link(keys)

    /** Lists [keys] as separate people again, undoing [showAsOne]. */
    fun showSeparately(keys: Collection<CardKey>) = sync.unlink(keys)

    /** Stops suggesting that [keys] are the same person. */
    fun notDuplicates(keys: Set<CardKey>) = sync.dismiss(keys)

    /** Asks for a vCard file and holds it in [importing] for the import sheet. */
    fun pickImport() {
        val files = files ?: return
        scope.launch { importing = files.pick() }
    }

    fun import(
        plan: ImportPlan,
        mode: ImportMode,
    ) {
        plan.edits(mode, ::newId).forEach { sync.add(it, ::sent) }
        importing = null
    }

    /** Saves [cards] as one vCard file in the downloads. */
    fun export(cards: List<ContactCard>) {
        val files = files ?: return
        scope.launch {
            val name = if (cards.size == 1) "${cards.single().displayName}.vcf" else "Contacts.vcf"
            val what = if (cards.size == 1) "contact file" else "contacts file"
            notice = if (files.save(name, cards.map { it.card })) "Saved $what" else "Couldn’t save the $what"
        }
    }

    /** A new group named [name] in [book] holding [members]. */
    fun createGroup(
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
        return ContactEdit.Create(newId(), book, group).also { sync.add(it, ::sent) }.key
    }

    /** Adds [cards] to [group]. Cards without a uid cannot be members, so they stay out. */
    fun addToGroup(
        group: ContactCard,
        cards: List<ContactCard>,
    ) {
        val members = group.card.members.orEmpty() + cards.mapNotNull { it.card.uid }.associateWith { true }
        update(group, group.card.copy(members = members))
    }

    /** Saves [after] over [card], at once on screen and on the server once it is reachable. */
    fun update(
        card: ContactCard,
        after: Card,
    ) = sync.add(ContactEdit.Update(newId(), card.key, card.card, after), ::sent)

    /** Saves a refused change as a new card in [book] instead. */
    fun saveElsewhere(
        key: CardKey,
        book: BookKey,
    ) {
        val card = card(key) ?: return
        sync.drop(key)
        sync.add(ContactEdit.Create(newId(), book, card.card.copy(id = null, uid = newUid())), ::sent)
    }

    /** Drops the refused change of [key], showing the card as the server has it. */
    fun discard(key: CardKey) = sync.drop(key)

    /** Keeps the device's view, and the avatars mail shows, in step with a change the server accepted. */
    private fun sent(update: (ContactsSnapshot) -> ContactsSnapshot) {
        val before = snapshot ?: return
        val after = update(before)
        snapshot = after
        avatars?.changed(before.cards, after.cards)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()

    @OptIn(ExperimentalUuidApi::class)
    private fun newUid(): String = "urn:uuid:${Uuid.random()}"
}

/** How many of the most frequent people the list shows above everyone. */
internal const val FREQUENT_SHOWN = 5
