package com.otus.mail.feature.contacts.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarOrigin
import com.otus.avatar.AvatarStyle
import com.otus.avatar.GeneratedAvatar
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.jscontact.Media
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.photo
import kotlin.io.encoding.Base64

/**
 * What the editor shows for the contact it is making: its style, else [photo], the draft photo's
 * decoded bytes, else initials. The caller keeps [photo] across edits, because the avatar cache
 * knows an image by its byte array, and decoding again on every typed letter would flash the initials.
 */
internal fun ContactDraft.look(photo: ByteArray?): ResolvedAvatar {
    val name = fullName.ifEmpty { emails.firstOrNull()?.value.orEmpty() }.ifEmpty { "?" }
    return contactLook(generatedAvatar(name, emails.firstOrNull()?.value ?: name), style, photo)
}

/** A contact's look: [style] when it has one, else [photo], else the [fallback] initials. */
internal fun contactLook(
    fallback: GeneratedAvatar,
    style: AvatarStyle?,
    photo: ByteArray?,
): ResolvedAvatar =
    when {
        style != null -> ResolvedAvatar(fallback, origin = AvatarOrigin.Contact, style = style)
        photo != null -> ResolvedAvatar(fallback, AvatarImage(photo), AvatarOrigin.Contact)
        else -> ResolvedAvatar(fallback)
    }

/** Whether the look is the contact's own style or photo rather than initials. */
internal val ResolvedAvatar.isLooked: Boolean get() = origin == AvatarOrigin.Contact

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
