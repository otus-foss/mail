package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.Name
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactEdit
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.ContactStore
import com.otusfoss.mail.feature.contacts.domain.ContactWriteException
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A large import saves the queue per batch, not per card, and refreshes avatars once (issue #281). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactSyncBatchesTest {
    private val book = BookKey("ada", "a", "b")

    @Test
    fun aLargeImportSavesTheQueueOncePerBatch() =
        runTest {
            val saved = CountingMapStore()
            val repository = CreatingRepository()
            val sync = ContactSync(repository, DeviceStore(saved, ContactStore), this)
            var flushes = 0
            sync.add(creates(IMPORTED), { flushes++ }) {}
            runCurrent()
            assertEquals(IMPORTED, repository.created)
            assertTrue(sync.edits.isEmpty())
            assertTrue(
                saved.writes <= IMPORTED / SAVE_BATCH + 2,
                "${saved.writes} saves: one to queue, one per batch, and one at the end",
            )
            assertEquals(1, flushes, "Avatars and the inbox refresh once per flush, not per card")
            assertTrue(
                ContactStore.EDITS
                    .of(saved.read())
                    .orEmpty()
                    .isEmpty(),
            )
        }

    @Test
    fun aBatchKeepsItsNewCardsAsSentBeforeTheyGo() =
        runTest {
            val saved = CountingMapStore()
            val repository = CreatingRepository(failure = ContactWriteException(ContactWriteFailure.Offline))
            var flushes = 0
            ContactSync(repository, DeviceStore(saved, ContactStore), this).add(creates(3), { flushes++ }) {}
            runCurrent()
            val queued =
                ContactStore.EDITS
                    .of(saved.read())
                    .orEmpty()
                    .filterIsInstance<ContactEdit.Create>()
            assertEquals(3, queued.size)
            assertTrue(queued.all { it.sent == it.card }, "A lost answer is looked into after a restart")
            assertEquals(0, flushes, "Nothing was sent")
        }

    private fun creates(count: Int) =
        (1..count).map { ContactEdit.Create("$it", book, Card(name = Name(full = "Card $it"), uid = "urn:uuid:$it")) }

    private class CountingMapStore : MemoryMapStore() {
        var writes = 0

        override fun write(values: Map<String, String>) {
            writes++
            super.write(values)
        }
    }

    /** Stores each card it is sent, or throws [failure] for every one. */
    private class CreatingRepository(
        private val failure: Exception? = null,
    ) : ContactRepository {
        var created = 0

        override suspend fun load() = ContactsSnapshot(emptyList(), emptyList(), true)

        override suspend fun create(
            book: BookKey,
            card: Card,
        ): CardKey {
            failure?.let { throw it }
            created++
            return CardKey(book.loginId, book.accountId, "server-$created")
        }

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) = Unit

        override suspend fun delete(keys: List<CardKey>) = Unit
    }

    private companion object {
        const val IMPORTED = 500
    }
}
