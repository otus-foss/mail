package com.otus.mail.feature.contacts.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ContactAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.jscontact.Media
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps sender avatars in step with contact changes the server accepted. Without it, mail keeps
 * the photo it found before, or none, for as long as the avatar cache holds it. An address whose
 * card now has an inline photo shows it at once; any other changed address is looked up again.
 * Then [refresh] redraws the mail.
 */
internal class ContactAvatarUpdates(
    private val avatars: AvatarService,
    private val scope: CoroutineScope,
    private val refresh: () -> Unit,
) {
    fun changed(
        before: List<ContactCard>,
        after: List<ContactCard>,
    ) {
        val changes = photoChanges(before, after)
        if (changes.isEmpty()) return
        scope.launch {
            changes.forEach { (address, photo) ->
                avatars.saved(AvatarRequest(address.loginId, address.email), photo?.toAvatar())
            }
            refresh()
        }
    }
}

/** An address as one login's contacts know it. */
internal data class PhotoAddress(
    val loginId: String,
    val email: String,
)

/** Each address whose photo differs between [before] and [after], with the photo it has now. */
internal fun photoChanges(
    before: List<ContactCard>,
    after: List<ContactCard>,
): Map<PhotoAddress, Media?> {
    val old = photosByAddress(before)
    val new = photosByAddress(after)
    return (old.keys + new.keys).filter { old[it] != new[it] }.associateWith { new[it] }
}

private fun photosByAddress(cards: List<ContactCard>): Map<PhotoAddress, Media?> =
    cards
        .flatMap { card ->
            card.emails.map { PhotoAddress(card.key.loginId, avatarEmailKey(it.value)) to card.card.photo }
        }.groupBy({ it.first }, { it.second })
        .mapValues { (_, photos) -> photos.firstNotNullOfOrNull { it } }

private fun Media.toAvatar(): ContactAvatar.Photo? =
    inlineBytes()?.let { ContactAvatar.Photo(AvatarImage(it), mediaType ?: DEFAULT_PHOTO_TYPE) }

private const val DEFAULT_PHOTO_TYPE = "image/jpeg"
