package com.otus.mail.feature.contacts.domain

import com.otus.avatar.ContactAvatar
import com.otus.avatar.withAvatarStyle
import com.otus.jscontact.Card
import com.otus.jscontact.Media
import com.otus.jscontact.MediaKind
import kotlin.io.encoding.Base64

/**
 * This card with [avatar] as its look: a photo replaces its photos and style, a style its photos,
 * and null takes both away, so every contact app shows the same.
 */
fun Card.withAvatar(avatar: ContactAvatar?): Card =
    when (avatar) {
        is ContactAvatar.Photo -> withPhoto(avatar.toMedia()).withAvatarStyle(null)
        is ContactAvatar.Styled -> withPhoto(null).withAvatarStyle(avatar.style)
        null -> withPhoto(null).withAvatarStyle(null)
    }

/** The photo as an inline `data:` URI: Stalwart refuses a `blobId` in `media`, and every contact app shows it. */
fun ContactAvatar.Photo.toMedia(): Media =
    Media(MediaKind.Photo, uri = "data:$mediaType;base64,${Base64.encode(image.bytes)}", mediaType = mediaType)
