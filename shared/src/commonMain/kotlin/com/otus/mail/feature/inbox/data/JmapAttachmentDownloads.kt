package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage

/**
 * Streams an attached file's blob from the download URL of the message's account (RFC 8620,
 * section 6.2), piece by piece, so its size is not limited by memory.
 */
class JmapAttachmentDownloads(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : AttachmentStream {
    override suspend fun stream(
        message: MailMessage,
        file: MailAttachment,
        receive: suspend (ByteArray) -> Unit,
    ): Boolean =
        mailCallOr(false) {
            val account = mailAccount(connections, logins, message.location.accountId) ?: return@mailCallOr false
            account.connection.downloadStream(account.id, file.blobId, file.type, file.name, receive)
            true
        }
}
