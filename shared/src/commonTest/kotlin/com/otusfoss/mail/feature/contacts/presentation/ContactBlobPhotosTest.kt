package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.EmailAddress
import com.otusfoss.core.jscontact.Media
import com.otusfoss.core.jscontact.MediaKind
import com.otusfoss.core.jscontact.Name
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import com.otusfoss.mail.feature.contacts.domain.photo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A photo the server gives back as a blob still shows on the contact list (issue #224). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactBlobPhotosTest {
    private val book = BookKey("ada", "a", "b")
    private val key = CardKey("ada", "a", "c1")
    private val image = byteArrayOf(1, 2, 3)

    @Test
    fun aPhotoKeptAsABlobShowsOnceItIsRead() =
        runTest {
            val repository = BlobRepository(blobCard("b1"), mapOf("b1" to image))
            val state = state(repository)
            state.loadCards()
            runCurrent()
            val photo =
                state.cards
                    .single()
                    .card.photo
            assertContentEquals(image, photo?.inlineBytes())
            assertNull(photo?.blobId, "A later change sends the photo inline, as Stalwart wants it")
            assertEquals(listOf("b1"), repository.read)
        }

    @Test
    fun eachBlobIsReadOnce() =
        runTest {
            val repository = BlobRepository(blobCard("b1"), mapOf("b1" to image))
            val state = state(repository)
            state.loadCards()
            runCurrent()
            state.loadCards()
            runCurrent()
            assertEquals(listOf("b1"), repository.read)
        }

    @Test
    fun aBlobThatCannotBeReadIsAskedForAgainOnTheNextLoad() =
        runTest {
            val repository = BlobRepository(blobCard("b1"), emptyMap())
            val state = state(repository)
            state.loadCards()
            runCurrent()
            assertNull(
                state.cards
                    .single()
                    .card.photo
                    ?.inlineBytes(),
            )
            state.loadCards()
            runCurrent()
            assertEquals(listOf("b1", "b1"), repository.read)
        }

    @Test
    fun aNewPhotoOnTheServerIsReadAgain() =
        runTest {
            val newer = byteArrayOf(4, 5)
            val repository = BlobRepository(blobCard("b1"), mapOf("b1" to image, "b2" to newer))
            val state = state(repository)
            state.loadCards()
            runCurrent()
            repository.card = blobCard("b2")
            state.loadCards()
            runCurrent()
            assertContentEquals(
                newer,
                state.cards
                    .single()
                    .card.photo
                    ?.inlineBytes(),
            )
        }

    private fun TestScope.state(repository: ContactRepository) =
        ContactsState(repository, ContactSync(repository, null, this), this)

    private fun blobCard(blobId: String) =
        ContactCard(
            key,
            Card(
                name = Name(full = "Ada"),
                emails = mapOf("e" to EmailAddress("ada@example.org")),
                media = mapOf("photo" to Media(MediaKind.Photo, blobId = blobId, mediaType = "image/png")),
            ),
            listOf(book),
        )

    /** Holds one [card] and serves the photo [blobs] it has, recording each blob it is asked for. */
    private class BlobRepository(
        var card: ContactCard,
        private val blobs: Map<String, ByteArray>,
    ) : ContactRepository {
        val read = mutableListOf<String>()

        override suspend fun load() = ContactsSnapshot(emptyList(), listOf(card), true)

        override suspend fun photo(
            key: CardKey,
            photo: Media,
        ): ByteArray? {
            val blobId = photo.blobId ?: return null
            read += blobId
            return blobs[blobId]
        }

        override suspend fun create(
            book: BookKey,
            card: Card,
        ) = CardKey(book.loginId, book.accountId, "new")

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) = Unit

        override suspend fun delete(keys: List<CardKey>) = Unit
    }
}
