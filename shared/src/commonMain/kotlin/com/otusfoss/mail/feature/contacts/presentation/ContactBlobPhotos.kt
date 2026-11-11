package com.otusfoss.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.core.jscontact.Media
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.photo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64

/**
 * The photos of cards that the server keeps as blobs, read through [repository] and kept as
 * inline photos, so the contact list and pages draw them. A server such as Stalwart gives back a
 * photo sent as a `data:` URI as a blob, so without this a new photo would turn back into initials
 * on the next load. Each blob is read once; one that could not be read is tried on the next load.
 */
internal class ContactBlobPhotos(
    private val repository: ContactRepository,
    private val scope: CoroutineScope,
) {
    var fetched by mutableStateOf(emptyMap<BlobPhoto, Media>())
        private set

    private val lock = Mutex()

    /** Reads the blob photos of [cards] it does not have yet, up to [MAX_BLOB_PHOTOS] in all. */
    fun fetch(cards: List<ContactCard>) {
        scope.launch {
            lock.withLock {
                cards
                    .mapNotNull { card -> card.blobPhoto()?.let { card to it } }
                    .distinctBy { it.second }
                    .filter { (_, blob) -> blob !in fetched }
                    .take((MAX_BLOB_PHOTOS - fetched.size).coerceAtLeast(0))
                    .forEach { (card, blob) -> read(card, blob) }
            }
        }
    }

    private suspend fun read(
        card: ContactCard,
        blob: BlobPhoto,
    ) {
        val photo = card.card.photo ?: return
        val bytes =
            try {
                repository.photo(card.key, photo)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        if (bytes != null) fetched = fetched + (blob to photo.inlined(bytes))
    }
}

/** A photo blob of one JMAP account. */
internal data class BlobPhoto(
    val loginId: String,
    val accountId: String,
    val blobId: String,
)

/** [cards], each whose photo is a blob in [fetched] showing it as an inline photo instead. */
internal fun List<ContactCard>.withFetchedPhotos(fetched: Map<BlobPhoto, Media>): List<ContactCard> =
    if (fetched.isEmpty()) this else map { it.withFetchedPhoto(fetched) }

private fun ContactCard.withFetchedPhoto(fetched: Map<BlobPhoto, Media>): ContactCard {
    val shown = blobPhoto()?.let(fetched::get) ?: return this
    val photo = card.photo
    val media = card.media.orEmpty().mapValues { (_, entry) -> if (entry == photo) shown else entry }
    return copy(card = card.copy(media = media))
}

/** The blob of the card's photo, unless the photo is inline already. */
private fun ContactCard.blobPhoto(): BlobPhoto? {
    val photo = card.photo?.takeUnless { it.uri.startsWith("data:") } ?: return null
    return photo.blobId?.let { BlobPhoto(key.loginId, key.accountId, it) }
}

/** This photo with [bytes] as a `data:` URI in place of its blob, as the app sends a new photo. */
private fun Media.inlined(bytes: ByteArray): Media {
    val type = mediaType ?: DEFAULT_TYPE
    return copy(uri = "data:$type;base64,${Base64.encode(bytes)}", blobId = null, mediaType = type)
}

/** How many blob photos the device keeps, about as many as a contact list shows photos for. */
private const val MAX_BLOB_PHOTOS = 500

private const val DEFAULT_TYPE = "image/jpeg"
