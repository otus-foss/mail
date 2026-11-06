package com.otus.mail.feature.read.domain

import com.otus.mail.core.files.StoredFile
import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage

/**
 * Attachments downloaded to the device, and the ones the user keeps offline. A download streams to
 * the app's private storage, so a file of any size fits; see docs/storage.md. Cancelling the
 * caller stops a download and leaves nothing of it behind.
 */
interface OfflineAttachments {
    /**
     * [file] on the device, downloaded first unless a copy is there. [progress] gets the bytes
     * received so far. Null when the server or the device failed.
     */
    suspend fun fetch(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit = {},
    ): StoredFile?

    /** [file]'s bytes, fetched as [fetch] does, for a thumbnail of a small image; null when it cannot be had. */
    suspend fun bytes(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray?

    /** Whether the user keeps [file] offline. */
    suspend fun isKept(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean

    /**
     * Keeps [file] offline, so trimming leaves it and it stays after restarts, downloading it first
     * when needed. Null when it could not be had.
     */
    suspend fun keep(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit = {},
    ): StoredFile?

    /** Deletes [file]'s copy from the device, kept or not. False when the device failed. */
    suspend fun remove(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean
}
