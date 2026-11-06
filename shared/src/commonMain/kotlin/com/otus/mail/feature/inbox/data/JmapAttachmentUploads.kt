package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.files.MAX_ATTACHMENT_BYTES
import com.otus.mail.core.files.UploadException
import com.otus.mail.core.files.UploadFailure
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.AttachmentUploads

/**
 * Uploads attachments to the account's blob endpoint (RFC 8620, section 6.1). A file larger than
 * the session's `maxSizeUpload` fails as [UploadFailure.TooLarge] before anything is sent.
 */
class JmapAttachmentUploads(
    private val connections: JmapConnections,
) : AttachmentUploads {
    override suspend fun maxBytes(accountId: String): Int =
        try {
            val account = connections.mailAccount(accountId) ?: throw UploadException(UploadFailure.Failed)
            (account.session.maxSizeUpload ?: MAX_ATTACHMENT_BYTES.toLong())
                .coerceIn(0L, MAX_ATTACHMENT_BYTES.toLong())
                .toInt()
        } catch (error: JmapException) {
            throw UploadException(UploadFailure.Failed, error)
        } catch (error: LoginStoreException) {
            throw UploadException(UploadFailure.Failed, error)
        }

    override suspend fun upload(
        accountId: String,
        bytes: ByteArray,
        type: String,
    ): Result<AttachmentBlob> =
        try {
            val account = connections.mailAccount(accountId) ?: throw UploadException(UploadFailure.Failed)
            Result.success(AttachmentBlob(accountId, account.connection.upload(account.id, bytes, type).blobId))
        } catch (error: UploadException) {
            Result.failure(error)
        } catch (error: JmapException.UploadTooLarge) {
            Result.failure(UploadException(UploadFailure.TooLarge, error))
        } catch (error: JmapException) {
            Result.failure(UploadException(UploadFailure.Failed, error))
        } catch (error: LoginStoreException) {
            Result.failure(UploadException(UploadFailure.Failed, error))
        }
}
