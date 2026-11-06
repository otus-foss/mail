package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.downloadStream
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage

/**
 * Streams an attached file's blob from the download URL of the message's account (RFC 8620,
 * section 6.2), piece by piece, so its size is not limited by memory.
 */
class JmapAttachmentDownloads(
    private val connections: JmapConnections,
) : AttachmentStream {
    override suspend fun stream(
        message: MailMessage,
        file: MailAttachment,
        receive: suspend (ByteArray) -> Unit,
    ): Boolean =
        jmapCallOr(false) {
            val account = connections.mailAccount(message.location.accountId) ?: return@jmapCallOr false
            account.connection.downloadStream(account.id, file.blobId, file.type, file.name, receive)
            true
        }
}
