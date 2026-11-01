package com.otus.mail.feature.contacts.presentation

import com.otus.jmap.JmapException
import com.otus.jscontact.Card
import com.otus.jscontact.Name
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactSyncTest {
    private val book = BookKey("ada", "a", "b")
    private val key = CardKey("ada", "a", "c1")

    @Test
    fun aChangeMadeWhileAnotherIsSendingIsSentAfterIt() =
        runTest {
            val repository = HeldRepository()
            val sync = sync(repository)
            val first = card("Ada")
            sync.add(listOf(ContactEdit.Update("1", key, card("Old"), first))) {}
            runCurrent()
            sync.add(listOf(ContactEdit.Update("2", key, first, card("Ada Lovelace")))) {}
            repository.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Ada", "Ada Lovelace"), repository.updates.map { it.name?.full })
            assertTrue(sync.edits.isEmpty())
        }

    @Test
    fun aDeleteMadeWhileTheCreateIsSendingDeletesTheNewCard() =
        runTest {
            val repository = HeldRepository()
            val sync = sync(repository)
            val create = ContactEdit.Create("1", book, card("Ada"))
            sync.add(listOf(create)) {}
            runCurrent()
            sync.add(listOf(ContactEdit.Delete("2", create.key))) {}
            repository.release.complete(Unit)
            runCurrent()
            assertEquals(listOf(listOf(CardKey("ada", "a", "server"))), repository.deletes)
        }

    @Test
    fun aServerErrorKeepsTheChangeWaitingWithoutCrashing() =
        runTest {
            val unauthorized = ContactWriteException(ContactWriteFailure.Offline, JmapException.Unauthorized())
            val repository = HeldRepository(failure = unauthorized)
            repository.release.complete(Unit)
            val sync = sync(repository)
            sync.add(listOf(ContactEdit.Update("1", key, card("Old"), card("Ada")))) {}
            runCurrent()
            assertEquals(listOf("1"), sync.edits.map { it.id })
            assertTrue(sync.refused.isEmpty())
        }

    @Test
    fun anUnexpectedFailureKeepsTheChangeWaiting() =
        runTest {
            val repository = HeldRepository(failure = IllegalStateException("unreadable reply"))
            repository.release.complete(Unit)
            val sync = sync(repository)
            sync.add(listOf(ContactEdit.Update("1", key, card("Old"), card("Ada")))) {}
            runCurrent()
            assertEquals(listOf("1"), sync.edits.map { it.id })
        }

    @Test
    fun aRefusedMergeKeepsTheCardsItWouldRemove() =
        runTest {
            val readOnly = ContactWriteException(ContactWriteFailure.ReadOnly, JmapException.Unauthorized())
            val repository = HeldRepository(createFailure = readOnly)
            repository.release.complete(Unit)
            val sync = sync(repository)
            val merge = ContactEdit.Create("1", book, card("Ada"))
            sync.add(listOf(merge, ContactEdit.Delete("2", key, waitsFor = merge.id))) {}
            runCurrent()
            assertTrue(repository.deletes.isEmpty())
            sync.drop(merge.key)
            assertTrue(sync.edits.isEmpty())
        }

    @Test
    fun aCreateWhoseAnswerWasLostIsFoundInsteadOfStoredTwice() =
        runTest {
            val repository = LostAnswerRepository()
            val sync = sync(repository)
            val create = ContactEdit.Create("1", book, card("Ada", uid = "urn:uuid:1"))
            sync.add(listOf(create)) {}
            runCurrent()
            assertEquals(listOf("1"), sync.edits.map { it.id }, "It waits after the lost answer")

            val later = card("Ada Lovelace", uid = "urn:uuid:1")
            sync.add(listOf(ContactEdit.Update("2", create.key, create.card, later))) {}
            runCurrent()
            assertEquals(1, repository.creates, "The card the lost answer hid is used")
            assertEquals(listOf("Ada Lovelace"), repository.updates.map { it.name?.full }, "It takes the later change")
            assertTrue(sync.edits.isEmpty())
            assertEquals(CardKey("ada", "a", "server"), sync.renamed[create.key])
        }

    private fun TestScope.sync(repository: ContactRepository) = ContactSync(repository, null, this)

    private fun card(
        name: String,
        uid: String? = null,
    ) = Card(name = Name(full = name), uid = uid)

    /** Stores the first card it is sent but loses the answer, as a timeout would. */
    private class LostAnswerRepository : ContactRepository {
        var creates = 0
        val updates = mutableListOf<Card>()
        private val stored = mutableSetOf<String?>()

        override suspend fun load() = ContactsSnapshot(emptyList(), emptyList(), true)

        override suspend fun create(
            book: BookKey,
            card: Card,
        ): CardKey {
            creates++
            stored += card.uid
            if (creates == 1) throw ContactWriteException(ContactWriteFailure.Offline)
            return CardKey(book.loginId, book.accountId, "other")
        }

        override suspend fun findCreated(
            book: BookKey,
            card: Card,
            ignore: Set<CardKey>,
        ): CardKey? = CardKey(book.loginId, book.accountId, "server").takeIf { card.uid in stored }

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) {
            updates += after
        }

        override suspend fun delete(keys: List<CardKey>) = Unit
    }

    /**
     * Holds every change until [release] completes, then records it or throws [failure], or
     * [createFailure] for a new card.
     */
    private class HeldRepository(
        private val failure: Exception? = null,
        private val createFailure: Exception? = failure,
    ) : ContactRepository {
        val release = CompletableDeferred<Unit>()
        val updates = mutableListOf<Card>()
        val deletes = mutableListOf<List<CardKey>>()

        override suspend fun load() = ContactsSnapshot(emptyList(), emptyList(), true)

        override suspend fun create(
            book: BookKey,
            card: Card,
        ): CardKey {
            release.await()
            createFailure?.let { throw it }
            return CardKey(book.loginId, book.accountId, "server")
        }

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) {
            release.await()
            failure?.let { throw it }
            updates += after
        }

        override suspend fun delete(keys: List<CardKey>) {
            release.await()
            failure?.let { throw it }
            deletes += keys
        }
    }
}
