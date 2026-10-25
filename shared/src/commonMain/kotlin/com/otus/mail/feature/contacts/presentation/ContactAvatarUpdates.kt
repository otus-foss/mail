package com.otus.mail.feature.contacts.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.AvatarStyle
import com.otus.avatar.ContactAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.avatar.avatarStyle
import com.otus.avatar.isAvatarCard
import com.otus.jscontact.Card
import com.otus.jscontact.Media
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import com.otus.mail.feature.contacts.domain.photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gives sender avatars the photos of the contact list the device already has, so mail shows them
 * at once and without asking the server. Mail to a login uses that login's cards first; while
 * [allAccounts] is on, an address with no photo there takes one from another login's card. An
 * address whose photo changed or went is looked up again. Then [refresh] redraws the mail.
 */
internal class ContactAvatarUpdates(
    private val avatars: AvatarService,
    private val scope: CoroutineScope,
    private val allAccounts: () -> Boolean,
    private val refresh: () -> Unit,
) {
    private val lock = Mutex()
    private var contacts: ContactsSnapshot? = null

    // The photo each address was last given, so only changes reach the avatar cache.
    private var given = emptyMap<PhotoAddress, ShownAvatar?>()

    /** Makes mail show the photos of [snapshot], the contact list as it is now. */
    fun shown(snapshot: ContactsSnapshot) {
        contacts = snapshot
        apply()
    }

    /** Gives every address the photo the contacts and [allAccounts] call for now. */
    fun apply() {
        scope.launch {
            val changed =
                lock.withLock {
                    val wanted = contacts?.let { contactPhotos(it, allAccounts()) } ?: return@launch
                    // At first every address counts, so photos removed while Mail was closed go too.
                    val changes = (given.keys + wanted.keys).filter { it !in given || given[it] != wanted[it] }
                    changes.forEach { address ->
                        avatars.saved(AvatarRequest(address.loginId, address.email), wanted[address]?.toAvatar())
                    }
                    given = wanted
                    changes.isNotEmpty()
                }
            if (changed) refresh()
        }
    }
}

/** An address as mail to one login sees it. */
internal data class PhotoAddress(
    val loginId: String,
    val email: String,
)

/** What an address shows: an avatar [style] from an avatar card, else a [photo]. */
internal data class ShownAvatar(
    val photo: Media? = null,
    val style: AvatarStyle? = null,
) {
    fun toAvatar(): ContactAvatar? =
        style?.let(ContactAvatar::Styled)
            ?: photo?.inlineBytes()?.let { ContactAvatar.Photo(AvatarImage(it), photo.mediaType ?: DEFAULT_PHOTO_TYPE) }
}

/**
 * What mail to each login should show for each address on a card. One of the user's avatar cards
 * wins, then a card in that login, then, with [allAccounts], another login's card. Null for an
 * address whose cards have no style and no inline photo, so a photo stored as a link or a blob is
 * still found on the server.
 */
internal fun contactPhotos(
    snapshot: ContactsSnapshot,
    allAccounts: Boolean,
): Map<PhotoAddress, ShownAvatar?> {
    val entries =
        snapshot.cards.flatMap { card ->
            val shown = card.card.shownAvatar()
            card.emails.map { PhotoEntry(card.key.loginId, avatarEmailKey(it.value), card.card.isAvatarCard, shown) }
        }
    val logins = (snapshot.books.map { it.key.loginId } + entries.map { it.loginId }).toSet()
    return logins
        .flatMap { login ->
            entries
                .filter { allAccounts || it.loginId == login }
                .groupBy { it.email }
                .map { (email, found) ->
                    val ranked = found.sortedWith(compareBy({ !it.avatarCard }, { it.loginId != login }))
                    PhotoAddress(login, email) to ranked.firstNotNullOfOrNull { it.shown }
                }
        }.toMap()
}

private fun Card.shownAvatar(): ShownAvatar? =
    avatarStyle?.let { ShownAvatar(style = it) }
        ?: photo?.takeIf { it.inlineBytes() != null }?.let { ShownAvatar(photo = it) }

private class PhotoEntry(
    val loginId: String,
    val email: String,
    val avatarCard: Boolean,
    val shown: ShownAvatar?,
)

private const val DEFAULT_PHOTO_TYPE = "image/jpeg"
