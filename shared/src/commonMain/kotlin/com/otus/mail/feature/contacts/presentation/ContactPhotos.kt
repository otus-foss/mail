package com.otus.mail.feature.contacts.presentation

import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.ContactRowLabel
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.Recipient
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_hide_image
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_work
import com.otus.jscontact.Media
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.photo
import com.otus.mail.feature.inbox.presentation.components.chipAvatar
import org.jetbrains.compose.resources.DrawableResource
import kotlin.io.encoding.Base64

/**
 * What the editor shows for the contact it is making: [photo], the draft photo's decoded bytes,
 * else initials. The caller keeps [photo] across edits, because the avatar cache knows an image
 * by its byte array, and decoding again on every typed letter would flash the initials.
 */
internal fun ContactDraft.avatar(photo: ByteArray?): ChipAvatar {
    val name = fullName.ifEmpty { emails.firstOrNull()?.value.orEmpty() }.ifEmpty { "?" }
    val generated = chipAvatar(null, name, emails.firstOrNull()?.value ?: name)
    return photo?.let { ChipAvatar(generated.initials, generated.colorIndex, it) } ?: generated
}

/** This avatar with [photo]'s image when it is inline. */
internal fun ChipAvatar.withPhoto(photo: Media?): ChipAvatar =
    photo?.inlineBytes()?.let { ChipAvatar(initials, colorIndex, it) } ?: this

/**
 * The image of a photo stored inline as a `data:` URI; null for a link or a blob. The same photo
 * gives the same bytes every time, because the avatar cache knows an image by its byte array, and
 * new bytes on each redraw, such as when a sheet opens, would flash the initials.
 */
internal fun Media.inlineBytes(): ByteArray? {
    if (uri !in decodedPhotos && decodedPhotos.size >= DECODED_PHOTOS) decodedPhotos.clear()
    return decodedPhotos.getOrPut(uri) { dataBytes(uri) ?: NO_PHOTO }.takeIf { it !== NO_PHOTO }
}

private val decodedPhotos = mutableMapOf<String, ByteArray>()
private val NO_PHOTO = ByteArray(0)
private const val DECODED_PHOTOS = 200

/** The bytes of a `data:` URI with base64 content; null for any other kind of link. */
private fun dataBytes(uri: String): ByteArray? {
    val comma = uri.indexOf(',')
    val inline = uri.startsWith("data:") && comma >= 0 && uri.substring(0, comma).endsWith(";base64")
    return if (inline) runCatching { Base64.decode(uri.substring(comma + 1)) }.getOrNull() else null
}

/**
 * The photo menu of a contact or group: Choose photo, or Change photo while it [hasPhoto], where
 * a photo can be picked, and Remove photo while it has one.
 */
internal fun photoChoices(
    hasPhoto: Boolean,
    canPick: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
): List<OptionsItem> =
    listOfNotNull(
        OptionsItem.Action(if (hasPhoto) "Change photo" else "Choose photo", Res.drawable.symbol_image, onPick).takeIf {
            canPick
        },
        OptionsItem.Action("Remove photo", Res.drawable.symbol_hide_image, onRemove).takeIf { hasPhoto },
    )
