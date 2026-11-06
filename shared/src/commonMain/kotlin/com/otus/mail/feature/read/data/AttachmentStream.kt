package com.otus.mail.feature.read.data

import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage

fun interface AttachmentStream {
    /**
     * Sends [file]'s bytes to [receive] piece by piece, in order. False when the server could not
     * be reached or refused, also after some pieces arrived. An exception from [receive] ends the
     * transfer and propagates.
     */
    suspend fun stream(
        message: MailMessage,
        file: MailAttachment,
        receive: suspend (ByteArray) -> Unit,
    ): Boolean
}
