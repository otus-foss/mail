package com.otus.mail.feature.contacts.data

import com.otus.avatar.MAX_AVATAR_BYTES
import com.otus.jmap.JmapConnection
import com.otus.jscontact.Media

/**
 * The image of [photo] in [accountId], which the server keeps as a blob (RFC 9610, section 3.5);
 * null for a photo with no blob or of a type avatars do not decode. Servers such as Stalwart give
 * back a photo sent as a `data:` URI this way.
 */
internal suspend fun JmapConnection.photo(
    accountId: String,
    photo: Media,
): ByteArray? {
    val blobId = photo.blobId
    val type = photo.mediaType?.takeIf { it in PHOTO_TYPES }
    if (blobId == null || type == null) return null
    val blob = download(accountId, blobId, type, MAX_AVATAR_BYTES, name = PHOTO_NAME)
    return blob.bytes.takeIf { it.isNotEmpty() && blob.mediaType in PHOTO_TYPES }
}

private const val PHOTO_NAME = "photo"

// The images the avatar library decodes when it loads contact photos for mail.
private val PHOTO_TYPES = setOf("image/png", "image/jpeg", "image/webp")
