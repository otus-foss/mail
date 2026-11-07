package com.otus.mail.core.messages.domain

import kotlinx.serialization.Serializable

/**
 * A file attached to a draft, [size] bytes of [type]. [blob] is its upload, which belongs to one
 * account; a draft moved to another account needs it uploaded there too. An [inline] image shows
 * in the body where the HTML names it by [cid] (RFC 2392) instead of as a file.
 */
@Serializable
data class DraftAttachment(
    val id: String,
    val name: String,
    val type: String,
    val size: Long,
    val blob: AttachmentBlob? = null,
    val inline: Boolean = false,
) {
    val cid: String get() = "$id@otus.mail"

    /** Whether the file is an image, which can show in the body. */
    val image: Boolean get() = type.startsWith("image/")

    /** Whether [accountId] holds the upload, so a message there can carry the file. */
    fun uploadedTo(accountId: String): Boolean = blob?.accountId == accountId
}

/** An uploaded file: blob [blobId] of account [accountId] (RFC 8620, section 6.1). */
@Serializable
data class AttachmentBlob(
    val accountId: String,
    val blobId: String,
)
