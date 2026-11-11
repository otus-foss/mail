package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.files.MAX_ATTACHMENT_BYTES
import com.otusfoss.mail.core.files.UploadException
import com.otusfoss.mail.core.messages.domain.AttachmentBlob
import com.otusfoss.mail.core.messages.domain.accountId

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
