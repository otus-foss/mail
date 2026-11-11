package com.otusfoss.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.core.jscontact.Card
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactEdit
import com.otusfoss.mail.feature.contacts.domain.ContactLinks
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.ContactStore
import com.otusfoss.mail.feature.contacts.domain.ContactWriteException
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import com.otusfoss.mail.feature.contacts.domain.afterSent
import com.otusfoss.mail.feature.contacts.domain.enqueue
import com.otusfoss.mail.feature.contacts.domain.isHeld
import com.otusfoss.mail.feature.contacts.domain.rekey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val ContactEdit.dependsOn: String? get() = (this as? ContactEdit.Delete)?.waitsFor

/** Whether the server has refused neither this change nor the one it waits for. */
internal fun ContactEdit.isReady(refused: Map<String, ContactWriteFailure>): Boolean =
    id !in refused && !isHeld(refused)

/** The changes ready to send, in order, that are not about the card of one of [passed], nor wait for one of them. */
private fun List<ContactEdit>.readyToSend(
    refused: Map<String, ContactWriteFailure>,
    passed: List<ContactEdit>,
): Sequence<ContactEdit> =
    asSequence()
        .filter { edit -> edit.isReady(refused) && passed.none { it.key == edit.key || it.id == edit.dependsOn } }

/**
 * How many changes a flush sends between two saves of the queue, so a large import does not
 * rewrite the whole queue for each card.
 */
internal const val SAVE_BATCH = 50

/** How [ContactSync] tells the contact list about a change the server accepted. */
internal typealias SentChange = ((ContactsSnapshot) -> ContactsSnapshot) -> Unit

/**
 * The device's contact data, kept in [store]: changes waiting for the server, cards shown as one,
 * and duplicate pairs dismissed. Changes go to [repository] one at a time, in order. Offline, the
 * rest wait for the next [flush]; a change the server refuses stays in [refused] until the user
 * saves it elsewhere or drops it, and so does each delete that waits for it. A change that fails
 * without a clear answer is passed over for that flush and set aside as
 * [ContactWriteFailure.Failing] after [MAX_TRIES] tries, so one bad card does not hold up the rest.
 * A flush saves the queue once per [SAVE_BATCH] changes and once at the end, not per change.
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

    // The changes of the current batch, and its new cards first kept as sent when it began.
    private var batch = emptySet<String>()
    private var firstTries = emptySet<String>()

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
        flushed: () -> Unit = {},
        sent: SentChange,
    ) {
        edits = changes.fold(edits) { queued, edit -> enqueue(queued, edit, inFlight) }
        save()
        scope.launch { flush(flushed, sent) }
    }

    /**
     * Sends every waiting change it can. It stops at the first one that finds no connection, and
     * after [MAX_UNCLEAR] changes in a row fail without a clear answer, which looks like the server
     * rather than those cards. A change that failed so is passed over, with the changes that need it.
     * [sent] runs for each change the server accepted, and [flushed] once at the end when there was one.
     */
    suspend fun flush(
        flushed: () -> Unit = {},
        sent: SentChange,
    ) {
        if (sending.isLocked) return
        sending.withLock {
            var passed = emptyList<ContactEdit>()
            var unclearInARow = 0
            var sentAny = false
            var next = edits.readyToSend(refused, passed).firstOrNull()
            try {
                while (next != null && unclearInARow < MAX_UNCLEAR) {
                    if (next.id !in batch) startBatch(passed)
                    val outcome = sendOne(next, sent)
                    if (outcome == SendOutcome.Offline) break
                    if (outcome == SendOutcome.Unclear) passed = passed + next
                    sentAny = sentAny || outcome == SendOutcome.Sent
                    unclearInARow = if (outcome == SendOutcome.Unclear) unclearInARow + 1 else 0
                    next = edits.readyToSend(refused, passed).firstOrNull()
                }
            } finally {
                if (batch.isNotEmpty()) save()
                batch = emptySet()
                firstTries = emptySet()
            }
            if (sentAny) flushed()
        }
    }

    /**
     * Saves the queue with the next [SAVE_BATCH] changes' new cards kept as sent, so a lost answer
     * to any of them is looked into on the next try, even after a restart.
     */
    private fun startBatch(passed: List<ContactEdit>) {
        val next = edits.readyToSend(refused, passed).take(SAVE_BATCH).toList()
        batch = next.map { it.id }.toSet()
        firstTries =
            next
                .filter { it is ContactEdit.Create && it.sent == null }
                .map { it.id }
                .toSet()
        edits = edits.map { if (it is ContactEdit.Create && it.id in firstTries) it.copy(sent = it.card) else it }
        save()
    }

    /**
     * Sends [edit] and moves its key, and any link to it, to the server's id for a new card. A
     * refused change is kept aside; one that keeps failing without a clear answer is too, after
     * [MAX_TRIES] tries.
     */
    private suspend fun sendOne(
        edit: ContactEdit,
        sent: SentChange,
    ): SendOutcome =
        try {
            inFlight = edit.id
            val created =
                when (edit) {
                    is ContactEdit.Create -> {
                        repository.createOnce(
                            edit,
                            tried = edit.sent?.takeIf { edit.id !in firstTries },
                            waiting = edits,
                        )
                    }

                    is ContactEdit.Update -> {
                        repository.sendUpdate(edit)
                    }

                    is ContactEdit.Delete -> {
                        repository.sendDelete(edit)
                    }
                }
            edits = edits.filterNot { it.id == edit.id }
            tries.keep(edits)
            if (created != null) {
                renamed = renamed + (edit.key to created)
                edits = rekey(edits, edit.key, created)
                links = ContactLinks(links.sets.map { set -> set.map { if (it == edit.key) created else it }.toSet() })
            }
            sent { it.copy(cards = afterSent(it.cards, edit, created)) }
            SendOutcome.Sent
        } catch (error: ContactWriteException) {
            if (tries.setsAside(edit.id, error.failure)) refused = refused + (edit.id to error.failure)
            error.failure.outcome
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Anything else, such as an unreadable reply, fails without a clear answer rather than crashing.
            val failing = ContactWriteFailure.Failing
            if (tries.setsAside(edit.id, failing)) refused = refused + (edit.id to failing)
            SendOutcome.Unclear
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

/**
 * Creates [edit]'s card, unless a try whose answer was lost stored it already: that card then takes
 * the changes made since, and is the new card. [tried] is the card an earlier batch sent; null on
 * the first try. The lookup skips the cards of the other [waiting] edits.
 */
private suspend fun ContactRepository.createOnce(
    edit: ContactEdit.Create,
    tried: Card?,
    waiting: List<ContactEdit>,
): CardKey {
    val earlier =
        tried?.let { card ->
            findCreated(edit.book, card, ignore = waiting.filter { it.id != edit.id }.map { it.key }.toSet())
        }
    if (tried != null && earlier != null) {
        update(earlier, tried, edit.card)
        return earlier
    }
    return create(edit.book, edit.card)
}
