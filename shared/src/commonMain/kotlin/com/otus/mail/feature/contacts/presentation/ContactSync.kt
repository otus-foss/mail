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
import com.otus.mail.feature.contacts.domain.rekey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How [ContactSync] tells the contact list about a change the server accepted. */
internal typealias SentChange = ((ContactsSnapshot) -> ContactsSnapshot) -> Unit

/**
 * The device's contact data, kept in [store]: changes waiting for the server, cards shown as one,
 * and duplicate pairs dismissed. Changes go to [repository] one at a time, in order. Offline, the
 * rest wait for the next [flush]; a change the server refuses stays in [refused] until the user
 * saves it elsewhere or drops it.
 */
internal class ContactSync(
    private val repository: ContactRepository,
    private val store: ContactStore?,
    private val scope: CoroutineScope,
) {
    private val saved = store?.read().orEmpty()

    var edits by mutableStateOf(ContactEdit.decode(saved[ContactStore.EDITS]))
        private set
    var refused by mutableStateOf(emptyMap<String, ContactWriteFailure>())
        private set
    var links by mutableStateOf(ContactLinks(KeySets.decode(saved[ContactStore.LINKS])))
        private set
    var dismissed by mutableStateOf(KeySets.decode(saved[ContactStore.DISMISSED]).toSet())
        private set

    private val sending = Mutex()

    fun add(
        edit: ContactEdit,
        sent: SentChange,
    ) {
        edits = enqueue(edits, edit)
        save()
        scope.launch { flush(sent) }
    }

    /** Sends every waiting change it can; stops at the first one that finds no connection. */
    suspend fun flush(sent: SentChange) {
        if (sending.isLocked) return
        sending.withLock {
            while (true) {
                val edit = edits.firstOrNull { it.id !in refused } ?: break
                try {
                    val created = send(edit)
                    edits = edits.filterNot { it.id == edit.id }
                    if (created != null) {
                        edits = rekey(edits, edit.key, created)
                        links =
                            ContactLinks(
                                links.sets.map { set ->
                                    set
                                        .map {
                                            if (it ==
                                                edit.key
                                            ) {
                                                created
                                            } else {
                                                it
                                            }
                                        }.toSet()
                                },
                            )
                    }
                    save()
                    sent { it.copy(cards = afterSent(it.cards, edit, created)) }
                } catch (error: ContactWriteException) {
                    if (error.failure == ContactWriteFailure.Offline) break
                    refused = refused + (edit.id to error.failure)
                }
            }
        }
    }

    /** Drops every waiting change of [key], refused or not. */
    fun drop(key: CardKey) {
        val gone = edits.filter { it.key == key }.map { it.id }.toSet()
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

    /** Returns the new card's key for a creation, else null. */
    private suspend fun send(edit: ContactEdit): CardKey? =
        when (edit) {
            is ContactEdit.Create -> {
                repository.create(edit.book, edit.card)
            }

            is ContactEdit.Update -> {
                repository.update(edit.key, edit.before, edit.after)
                null
            }

            is ContactEdit.Delete -> {
                repository.delete(listOf(edit.key))
                null
            }
        }

    private fun save() {
        store?.write(
            mapOf(
                ContactStore.EDITS to ContactEdit.encode(edits),
                ContactStore.LINKS to KeySets.encode(links.sets),
                ContactStore.DISMISSED to KeySets.encode(dismissed.toList()),
            ),
        )
    }
}
