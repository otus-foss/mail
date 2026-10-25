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
    val sync: ContactSync,
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

    fun refresh() {
        frequent?.refresh()
        if (loading) return
        loading = true
        scope.launch {
            try {
                val loaded = repository.load()
                snapshot = loaded
                avatars?.shown(loaded)
                if (loaded.failedLogins.isNotEmpty() && loaded.cards.isEmpty()) notice = "Couldn’t load your contacts."
                sync.flush(this@ContactsState::sent)
            } finally {
                loading = false
            }
        }
    }

    /** Asks for a vCard file and holds it in [importing] for the import sheet. */
    fun pickImport() {
        val files = files ?: return
        scope.launch { importing = files.pick() }
    }

    fun import(
        plan: ImportPlan,
        mode: ImportMode,
    ) {
        plan.edits(mode) { newId() }.forEach(::send)
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

    /** Queues [edit]: it shows at once and goes to the server once it is reachable. */
    fun send(edit: ContactEdit) = sync.add(edit, ::sent)

    /** Keeps the device's view, and the avatars mail shows, in step with a change the server accepted. */
    private fun sent(update: (ContactsSnapshot) -> ContactsSnapshot) {
        val after = snapshot?.let(update) ?: return
        snapshot = after
        avatars?.shown(after)
    }
}

/** How many of the most frequent people the list shows above everyone. */
internal const val FREQUENT_SHOWN = 5

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

@OptIn(ExperimentalUuidApi::class)
internal fun newUid(): String = "urn:uuid:${Uuid.random()}"
