package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.feature.inbox.domain.AttachmentDownloads
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage

/** Downloads an attached file's blob from the download URL of the message's account (RFC 8620, section 6.2). */
class JmapAttachmentDownloads(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : AttachmentDownloads {
    override suspend fun download(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray? =
        try {
            mailAccount(connections, logins, message.location.accountId)?.let { account ->
                account.connection.download(account.id, file.blobId, file.type, MAX_FILE_BYTES, file.name).bytes
            }
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    private companion object {
        /** The JMAP client's default response limit, which a download may not pass. */
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
    }
}
