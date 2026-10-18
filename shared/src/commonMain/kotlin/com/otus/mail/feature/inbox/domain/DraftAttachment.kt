package com.otus.mail.feature.inbox.domain

/**
 * A file attached to a draft, [size] bytes of [type]. [blob] is its upload, which belongs to one
 * account; a draft moved to another account needs it uploaded there too. An [inline] image shows
 * in the body where the HTML names it by [cid] (RFC 2392) instead of as a file.
 */
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
data class AttachmentBlob(
    val accountId: String,
    val blobId: String,
)

/** A file the user picked. [read] loads its bytes, again whenever it is uploaded. */
class PickedFile(
    val name: String,
    val type: String,
    val size: Long,
    val read: suspend () -> ByteArray,
)

/** Lets the user pick files to attach; empty when they pick none. */
fun interface AttachmentFiles {
    suspend fun pick(): List<PickedFile>
}

/** Why a file could not be uploaded. */
enum class UploadFailure { TooLarge, Failed }

/** The failure an [AttachmentUploads] result carries. */
class UploadException(
    val failure: UploadFailure,
    cause: Throwable? = null,
) : Exception("The file could not be uploaded: $failure", cause)

/** Uploads files to an account, for drafts to carry. */
fun interface AttachmentUploads {
    /** Uploads [bytes] of [type] and returns the blob; a failed result holds an [UploadException]. */
    suspend fun upload(
        accountId: String,
        bytes: ByteArray,
        type: String,
    ): Result<AttachmentBlob>
}
