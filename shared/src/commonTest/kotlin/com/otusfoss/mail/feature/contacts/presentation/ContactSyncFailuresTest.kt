package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.Name
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactEdit
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.ContactWriteException
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the queue goes on when single cards fail, as in an import of many cards (issue #231). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactSyncFailuresTest {
    private val book = BookKey("ada", "a", "b")

    @Test
    fun aCardThatKeepsFailingIsPassedOverAndThenSetAside() =
        runTest {
            val repository = ScriptedRepository(mapOf("Ada" to IllegalStateException("unreadable reply")))
            val sync = sync(repository)
            val ada = create("1", "Ada")
            sync.add(listOf(ada, create("2", "Grace"), create("3", "Alan"))) {}
            runCurrent()
            assertEquals(listOf("Grace", "Alan"), repository.created, "The cards after it are sent")
            assertEquals(listOf("1"), sync.edits.map { it.id })
            assertTrue(sync.refused.isEmpty(), "One failure may be the server's")

            repeat(MAX_TRIES - 1) { sync.flush {} }
            assertEquals(mapOf("1" to ContactWriteFailure.Failing), sync.refused)
            assertEquals(listOf("1"), sync.edits.map { it.id }, "It stays for the user to save elsewhere or drop")
            assertEquals(0, sync.edits.count { it.isReady(sync.refused) })
        }

    @Test
    fun aCardTheServerRefusesIsSetAsideAtOnce() =
        runTest {
            val refusal = ContactWriteException(ContactWriteFailure.Refused)
            val repository = ScriptedRepository(mapOf("Ada" to refusal))
            val sync = sync(repository)
            sync.add(listOf(create("1", "Ada"), create("2", "Grace"))) {}
            runCurrent()
            assertEquals(listOf("Grace"), repository.created)
            assertEquals(mapOf("1" to ContactWriteFailure.Refused), sync.refused)
        }

    @Test
    fun noConnectionKeepsEveryChangeWaiting() =
        runTest {
            val offline = ContactWriteException(ContactWriteFailure.Offline)
            val repository = ScriptedRepository(mapOf("Ada" to offline))
            val sync = sync(repository)
            sync.add(listOf(create("1", "Ada"), create("2", "Grace"))) {}
            runCurrent()
            repeat(MAX_TRIES) { sync.flush {} }
            assertTrue(repository.created.isEmpty())
            assertEquals(listOf("1", "2"), sync.edits.map { it.id })
            assertTrue(sync.refused.isEmpty(), "Being offline never sets a change aside")
        }

    @Test
    fun aServerFailingEveryCardIsNotAskedForEachOne() =
        runTest {
            val failing = ContactWriteException(ContactWriteFailure.Failing)
            val repository = ScriptedRepository(listOf("Ada", "Grace", "Alan").associateWith { failing })
            val sync = sync(repository)
            sync.add(listOf(create("1", "Ada"), create("2", "Grace"), create("3", "Alan"))) {}
            runCurrent()
            assertEquals(MAX_UNCLEAR, repository.tries)
        }

    @Test
    fun aDeleteWaitingForAPassedOverCardWaitsToo() =
        runTest {
            val repository = ScriptedRepository(mapOf("Ada" to IllegalStateException("unreadable reply")))
            val sync = sync(repository)
            val merged = create("1", "Ada")
            val old = CardKey("ada", "a", "old")
            sync.add(listOf(merged, ContactEdit.Delete("2", old, waitsFor = merged.id), create("3", "Grace"))) {}
            runCurrent()
            assertTrue(repository.deleted.isEmpty())
            assertEquals(listOf("Grace"), repository.created)
        }

    private fun TestScope.sync(repository: ContactRepository) = ContactSync(repository, null, this)

    private fun create(
        id: String,
        name: String,
    ) = ContactEdit.Create(id, book, Card(name = Name(full = name)))

    /** Stores each card it is sent, except that it throws the failure [failures] names for a card. */
    private class ScriptedRepository(
        private val failures: Map<String, Exception>,
    ) : ContactRepository {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<CardKey>()
        var tries = 0

        override suspend fun load() = ContactsSnapshot(emptyList(), emptyList(), true)

        override suspend fun create(
            book: BookKey,
            card: Card,
        ): CardKey {
            tries++
            val name = card.name?.full.orEmpty()
            failures[name]?.let { throw it }
            created += name
            return CardKey(book.loginId, book.accountId, "server-$name")
        }

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) = Unit

        override suspend fun delete(keys: List<CardKey>) {
            deleted += keys
        }
    }
}
