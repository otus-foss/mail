package com.otus.mail

import com.otus.mail.core.files.StoredFile
import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OfflineAttachments

/** Gives every image file the bytes of [photo] for its thumbnail; nothing is stored on the device. */
class PhotoAttachments(
    private val photo: () -> ByteArray,
) : OfflineAttachments {
    override suspend fun fetch(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredFile? = null

    override suspend fun bytes(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray? = photo().takeIf { file.type.startsWith("image/") }

    override suspend fun isKept(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = false

    override suspend fun keep(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredFile? = null

    override suspend fun remove(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = false
}
