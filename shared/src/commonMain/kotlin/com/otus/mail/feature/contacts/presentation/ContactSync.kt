package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactLinks
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
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

/** The first change ready to send that is not about the card of one of [passed], nor waits for one of them. */
private fun List<ContactEdit>.nextToSend(
    refused: Map<String, ContactWriteFailure>,
    passed: List<ContactEdit>,
): ContactEdit? =
    firstOrNull { edit -> edit.isReady(refused) && passed.none { it.key == edit.key || it.id == edit.dependsOn } }

/** What came of sending one change. */
private enum class Outcome { Sent, Refused, Unclear, Offline }

private val ContactWriteFailure.outcome: Outcome
    get() =
        when (this) {
            ContactWriteFailure.Offline -> Outcome.Offline
            ContactWriteFailure.Failing -> Outcome.Unclear
            else -> Outcome.Refused
        }

/** How often each change failed without a clear answer since the app started. */
private class FailedTries {
    private var counts = emptyMap<String, Int>()

    /**
     * Whether a change [id] that failed with [failure] is set aside now: a refused one at once, one
     * without a clear answer once it failed [MAX_TRIES] times, and one that found no connection never.
     */
    fun setsAside(
        id: String,
        failure: ContactWriteFailure,
    ): Boolean {
        if (failure != ContactWriteFailure.Failing) return failure != ContactWriteFailure.Offline
        val count = (counts[id] ?: 0) + 1
        counts = counts + (id to count)
        return count >= MAX_TRIES
    }

    /** Forgets the changes that are no longer in [edits]. */
    fun keep(edits: List<ContactEdit>) {
        counts = counts.filterKeys { id -> edits.any { it.id == id } }
    }
}

/** How many times a change may fail without a clear answer before it is set aside. */
internal const val MAX_TRIES = 3

/** How many changes in a row may fail without a clear answer before a flush stops. */
internal const val MAX_UNCLEAR = 2

/** How [ContactSync] tells the contact list about a change the server accepted. */
internal typealias SentChange = ((ContactsSnapshot) -> ContactsSnapshot) -> Unit

/**
 * The device's contact data, kept in [store]: changes waiting for the server, cards shown as one,
 * and duplicate pairs dismissed. Changes go to [repository] one at a time, in order. Offline, the
 * rest wait for the next [flush]; a change the server refuses stays in [refused] until the user
 * saves it elsewhere or drops it, and so does each delete that waits for it. A change that fails
 * without a clear answer is passed over for that flush and set aside as
 * [ContactWriteFailure.Failing] after [MAX_TRIES] tries, so one bad card does not hold up the rest.
 */
internal class ContactSync(
    private val repository: ContactRepository,
    private val store: DeviceStore<String, String>?,
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

    private val tries = FailedTries()

    init {
        reload()
    }

    /** Reads the store again, as after an account is removed and its changes with it. */
    fun reload() {
        val saved = store?.read().orEmpty()
        edits = ContactStore.EDITS.of(saved).orEmpty()
        links = ContactLinks(ContactStore.LINKS.of(saved).orEmpty())
        dismissed =
            ContactStore.DISMISSED
                .of(saved)
                .orEmpty()
                .toSet()
        refused = refused.filterKeys { id -> edits.any { it.id == id } }
        tries.keep(edits)
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

    /**
     * Sends every waiting change it can. It stops at the first one that finds no connection, and
     * after [MAX_UNCLEAR] changes in a row fail without a clear answer, which looks like the server
     * rather than those cards. A change that failed so is passed over, with the changes that need it.
     */
    suspend fun flush(sent: SentChange) {
        if (sending.isLocked) return
        sending.withLock {
            var passed = emptyList<ContactEdit>()
            var unclearInARow = 0
            var next = edits.nextToSend(refused, passed)
            while (next != null && unclearInARow < MAX_UNCLEAR) {
                val outcome = sendOne(next, sent)
                if (outcome == Outcome.Offline) break
                if (outcome == Outcome.Unclear) passed = passed + next
                unclearInARow = if (outcome == Outcome.Unclear) unclearInARow + 1 else 0
                next = edits.nextToSend(refused, passed)
            }
        }
    }

    /**
     * Sends [edit] and moves its key, and any link to it, to the server's id for a new card. A
     * refused change is kept aside; one that keeps failing without a clear answer is too, after
     * [MAX_TRIES] tries.
     */
    private suspend fun sendOne(
        edit: ContactEdit,
        sent: SentChange,
    ): Outcome =
        try {
            inFlight = edit.id
            val created =
                when (edit) {
                    is ContactEdit.Create -> create(edit)
                    is ContactEdit.Update -> repository.sendUpdate(edit)
                    is ContactEdit.Delete -> repository.sendDelete(edit)
                }
            edits = edits.filterNot { it.id == edit.id }
            tries.keep(edits)
            if (created != null) {
                renamed = renamed + (edit.key to created)
                edits = rekey(edits, edit.key, created)
                links = ContactLinks(links.sets.map { set -> set.map { if (it == edit.key) created else it }.toSet() })
            }
            save()
            sent { it.copy(cards = afterSent(it.cards, edit, created)) }
            Outcome.Sent
        } catch (error: ContactWriteException) {
            if (tries.setsAside(edit.id, error.failure)) refused = refused + (edit.id to error.failure)
            error.failure.outcome
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Anything else, such as an unreadable reply, fails without a clear answer rather than crashing.
            val failing = ContactWriteFailure.Failing
            if (tries.setsAside(edit.id, failing)) refused = refused + (edit.id to failing)
            Outcome.Unclear
        } finally {
            inFlight = null
        }

    /** Drops every waiting change of [key], refused or not, and the deletes that wait for them. */
    fun drop(key: CardKey) {
        val dropped = edits.filter { it.key == key }.map { it.id }.toSet()
        val gone = dropped + edits.filter { it.dependsOn in dropped }.map { it.id }
        edits = edits.filterNot { it.id in gone }
        refused = refused - gone
        tries.keep(edits)
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
        // Only these three change; the others, such as the frequent scores, stay.
        store?.update { saved ->
            saved +
                listOf(
                    ContactStore.EDITS.entry(edits),
                    ContactStore.LINKS.entry(links.sets),
                    ContactStore.DISMISSED.entry(dismissed.toList()),
                )
        }
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
