package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactLinks
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import com.otus.mail.feature.contacts.domain.KeySets
import com.otus.mail.feature.contacts.domain.afterSent
import com.otus.mail.feature.contacts.domain.enqueue
import com.otus.mail.feature.contacts.domain.isHeld
import com.otus.mail.feature.contacts.domain.rekey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val ContactEdit.dependsOn: String? get() = (this as? ContactEdit.Delete)?.waitsFor

/** Whether the server has refused neither this change nor the one it waits for. */
internal fun ContactEdit.isReady(refused: Map<String, ContactWriteFailure>): Boolean =
    id !in refused && !isHeld(refused)

/** How [ContactSync] tells the contact list about a change the server accepted. */
internal typealias SentChange = ((ContactsSnapshot) -> ContactsSnapshot) -> Unit

/**
 * The device's contact data, kept in [store]: changes waiting for the server, cards shown as one,
 * and duplicate pairs dismissed. Changes go to [repository] one at a time, in order. Offline, the
 * rest wait for the next [flush]; a change the server refuses stays in [refused] until the user
 * saves it elsewhere or drops it, and so does each delete that waits for it.
 */
internal class ContactSync(
    private val repository: ContactRepository,
    private val store: ContactStore?,
    private val scope: CoroutineScope,
) {
    var edits by mutableStateOf(emptyList<ContactEdit>())
        private set
    var refused by mutableStateOf(emptyMap<String, ContactWriteFailure>())
        private set
    var links by mutableStateOf(ContactLinks(emptyList()))
        private set
    var dismissed by mutableStateOf(emptySet<Set<CardKey>>())
        private set

    /** The server's key for each card created on the device, by the key it had until then. */
    var renamed by mutableStateOf(emptyMap<CardKey, CardKey>())
        private set

    private val sending = Mutex()

    // The change on its way to the server, which a new change must not join.
    private var inFlight: String? = null

    init {
        reload()
    }

    /** Reads the store again, as after an account is removed and its changes with it. */
    fun reload() {
        val saved = store?.read().orEmpty()
        edits = ContactEdit.decode(saved[ContactStore.EDITS])
        links = ContactLinks(KeySets.decode(saved[ContactStore.LINKS]))
        dismissed = KeySets.decode(saved[ContactStore.DISMISSED]).toSet()
        refused = refused.filterKeys { id -> edits.any { it.id == id } }
    }

    /** Queues [changes] and saves the queue once, so a large import does not save it per card. */
    fun add(
        changes: List<ContactEdit>,
        sent: SentChange,
    ) {
        edits = changes.fold(edits) { queued, edit -> enqueue(queued, edit, inFlight) }
        save()
        scope.launch { flush(sent) }
    }

    /** Sends every waiting change it can; stops at the first one that finds no connection. */
    suspend fun flush(sent: SentChange) {
        if (sending.isLocked) return
        sending.withLock {
            var next = edits.firstOrNull { it.isReady(refused) }
            while (next != null && sendOne(next, sent)) next = edits.firstOrNull { it.isReady(refused) }
        }
    }

    /**
     * Sends [edit] and moves its key, and any link to it, to the server's id for a new card. False
     * when there is no connection, so the changes after it wait too; a refused change is kept aside.
     */
    private suspend fun sendOne(
        edit: ContactEdit,
        sent: SentChange,
    ): Boolean =
        try {
            inFlight = edit.id
            val created =
                when (edit) {
                    is ContactEdit.Create -> create(edit)
                    is ContactEdit.Update -> repository.sendUpdate(edit)
                    is ContactEdit.Delete -> repository.sendDelete(edit)
                }
            edits = edits.filterNot { it.id == edit.id }
            if (created != null) {
                renamed = renamed + (edit.key to created)
                edits = rekey(edits, edit.key, created)
                links = ContactLinks(links.sets.map { set -> set.map { if (it == edit.key) created else it }.toSet() })
            }
            save()
            sent { it.copy(cards = afterSent(it.cards, edit, created)) }
            true
        } catch (error: ContactWriteException) {
            val offline = error.failure == ContactWriteFailure.Offline
            if (!offline) refused = refused + (edit.id to error.failure)
            !offline
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Anything else, such as an unreadable reply, waits like no connection rather than crash.
            false
        } finally {
            inFlight = null
        }

    /** Drops every waiting change of [key], refused or not, and the deletes that wait for them. */
    fun drop(key: CardKey) {
        val dropped = edits.filter { it.key == key }.map { it.id }.toSet()
        val gone = dropped + edits.filter { it.dependsOn in dropped }.map { it.id }
        edits = edits.filterNot { it.id in gone }
        refused = refused - gone
        save()
    }

    fun link(keys: Set<CardKey>) {
        links = links.link(keys)
        save()
    }

    fun unlink(keys: Collection<CardKey>) {
        links = keys.fold(links) { all, key -> all.without(key) }
        save()
    }

    fun dismiss(keys: Set<CardKey>) {
        dismissed = dismissed + setOf(keys)
        save()
    }

    /**
     * Creates [edit]'s card, unless a try whose answer was lost stored it already: that card then
     * takes the changes made since, and is the new card. The card is kept as sent before it goes,
     * so a lost answer is looked into on the next try, even after a restart.
     */
    private suspend fun create(edit: ContactEdit.Create): CardKey {
        val sent = edit.sent
        val others = edits.filter { it.id != edit.id }.map { it.key }.toSet()
        val earlier = sent?.let { repository.findCreated(edit.book, it, ignore = others) }
        if (sent != null && earlier != null) {
            repository.update(earlier, sent, edit.card)
            return earlier
        }
        edits = edits.map { if (it.id == edit.id) edit.copy(sent = edit.card) else it }
        save()
        return repository.create(edit.book, edit.card)
    }

    private fun save() {
        // A write replaces every saved value, so the others, such as the frequent scores, are kept.
        val storage = store ?: return
        storage.write(
            storage.read() +
                mapOf(
                    ContactStore.EDITS to ContactEdit.encode(edits),
                    ContactStore.LINKS to KeySets.encode(links.sets),
                    ContactStore.DISMISSED to KeySets.encode(dismissed.toList()),
                ),
        )
    }
}

private suspend fun ContactRepository.sendUpdate(edit: ContactEdit.Update): CardKey? {
    update(edit.key, edit.before, edit.after)
    return null
}

private suspend fun ContactRepository.sendDelete(edit: ContactEdit.Delete): CardKey? {
    delete(listOf(edit.key))
    return null
}
