package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.avatar.AvatarImage
import com.otusfoss.core.avatar.AvatarMark
import com.otusfoss.core.avatar.AvatarRequest
import com.otusfoss.core.avatar.AvatarService
import com.otusfoss.core.avatar.AvatarStyle
import com.otusfoss.core.avatar.ContactAvatar
import com.otusfoss.core.avatar.avatarEmailKey
import com.otusfoss.core.avatar.avatarStyle
import com.otusfoss.core.avatar.isAvatarCard
import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.Media
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import com.otusfoss.mail.feature.contacts.domain.photo
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
 * What mail to each login should show for each address on a card. Cards come in this order: the
 * login's own cards, its avatar cards first, then its other cards, then, with [allAccounts], other
 * logins' own cards and then their other cards. An own card is in a book of the user's that they
 * may write and that nobody shared with them; only own cards count as avatar cards, so a shared
 * book or another login cannot replace the user's choice. A Website style counts only on an own
 * card of the same login, since it fetches the website icon with Website icons off. Other styles
 * draw nothing from the network and count on any card, as in core's server lookup. Null for an
 * address whose cards have no style and no inline photo, so a photo stored as a link or a blob is
 * still found on the server.
 */
internal fun contactPhotos(
    snapshot: ContactsSnapshot,
    allAccounts: Boolean,
): Map<PhotoAddress, ShownAvatar?> {
    val ownBooks =
        snapshot.books
            .filter { it.canWrite && !it.isShared }
            .map { it.key }
            .toSet()
    val entries =
        snapshot.cards.flatMap { card ->
            val own = card.books.any { it in ownBooks }
            card.emails.map { PhotoEntry(card.key.loginId, avatarEmailKey(it.value), own, card.card) }
        }
    val logins = (snapshot.books.map { it.key.loginId } + entries.map { it.loginId }).toSet()
    return logins
        .flatMap { login ->
            entries
                .filter { allAccounts || it.loginId == login }
                .groupBy { it.email }
                .map { (email, found) ->
                    PhotoAddress(login, email) to
                        found
                            .sortedWith(
                                compareBy({ it.loginId != login }, { !it.own }, { !(it.own && it.card.isAvatarCard) }),
                            ).firstNotNullOfOrNull { it.shownTo(login) }
                }
        }.toMap()
}

private class PhotoEntry(
    val loginId: String,
    val email: String,
    val own: Boolean,
    val card: Card,
) {
    fun shownTo(login: String): ShownAvatar? =
        card.avatarStyle
            ?.takeIf { it.mark != AvatarMark.Website || (own && loginId == login) }
            ?.let { ShownAvatar(style = it) }
            ?: card.photo?.takeIf { it.inlineBytes() != null }?.let { ShownAvatar(photo = it) }
}

private const val DEFAULT_PHOTO_TYPE = "image/jpeg"
