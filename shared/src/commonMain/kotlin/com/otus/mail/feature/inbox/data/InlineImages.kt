package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.MailAccount
import kotlin.io.encoding.Base64

/**
 * Uploads each `data:` image in [html] (RFC 8620, section 6.1) and points it at its part with a
 * `cid:` URI instead, so the message carries the image rather than linking to it (RFC 2392).
 * Null [html] stays null.
 */
internal suspend fun MailAccount.inlineImages(html: String?): EmailHtml? = html?.let { uploaded(it) }

private suspend fun MailAccount.uploaded(html: String): EmailHtml {
    val found =
        dataImage
            .findAll(html)
            .map { it.value to it.destructured }
            .distinctBy { it.first }
            .toList()
    val images =
        found.mapIndexed { index, (uri, parts) ->
            val (type, data) = parts
            val upload = connection.upload(id, Base64.decode(data), type)
            uri to InlineImage("image$index@otus.mail", upload.blobId, upload.type.ifBlank { type })
        }
    val rewritten = images.fold(html) { text, (uri, image) -> text.replace(uri, "cid:${image.cid}") }
    return EmailHtml(rewritten, images.map { it.second })
}

private val dataImage = Regex("""data:([\w/+.-]+);base64,([A-Za-z0-9+/=]+)""")
