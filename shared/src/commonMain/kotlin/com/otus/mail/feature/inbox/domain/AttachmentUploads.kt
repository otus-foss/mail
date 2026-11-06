package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.files.MAX_ATTACHMENT_BYTES
import com.otus.mail.core.files.UploadException
import com.otus.mail.core.messages.domain.AttachmentBlob

/** Uploads files to an account, for drafts to carry. */
fun interface AttachmentUploads {
    /** The server upload limit, capped by the device's in-memory file limit. */
    suspend fun maxBytes(accountId: String): Int = MAX_ATTACHMENT_BYTES

    /** Uploads [bytes] of [type] and returns the blob; a failed result holds an [UploadException]. */
    suspend fun upload(
        accountId: String,
        bytes: ByteArray,
        type: String,
    ): Result<AttachmentBlob>
}
