package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.feature.inbox.domain.MessageContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.io.encoding.Base64

/**
 * [content] with each image its HTML shows by `cid:` (RFC 2392) downloaded from [parts] and
 * written into the HTML as a `data:` URI, since the sandboxed view loads nothing from the server.
 * An image over [MAX_INLINE_BYTES] or one that fails to download keeps its `cid:` address and
 * shows as missing; the rest of the message still opens.
 */
internal suspend fun MailAccount.withInlineImages(
    content: MessageContent,
    parts: List<EmailBodyPart>,
): MessageContent {
    val html = content.html
    val images =
        coroutineScope {
            parts.map { part -> async { part.cid.orEmpty() to dataUri(part) } }.awaitAll()
        }.mapNotNull { (cid, uri) -> uri?.let { cid.lowercase() to it } }
            .toMap()
    if (html == null || images.isEmpty()) return content
    val inlined = CID_URI.replace(html.html) { match -> images[match.groupValues[1].lowercase()] ?: match.value }
    return content.copy(html = html.copy(html = inlined))
}

private suspend fun MailAccount.dataUri(part: EmailBodyPart): String? {
    val blobId = part.blobId?.takeIf { part.size <= MAX_INLINE_BYTES } ?: return null
    // The type lands inside an HTML attribute, so only a plain image type is kept.
    val type = part.type?.lowercase()?.takeIf(IMAGE_MEDIA_TYPE::matches) ?: IMAGE_TYPE
    return try {
        val blob = connection.download(id, blobId, type, MAX_INLINE_BYTES, part.name ?: "image")
        "data:$type;base64,${Base64.encode(blob.bytes)}"
    } catch (expected: JmapException) {
        null
    }
}

/** Larger images stay out of the HTML, which the view holds in memory as text. */
private const val MAX_INLINE_BYTES = 5L * 1024 * 1024

private val IMAGE_MEDIA_TYPE = Regex("""image/[a-z0-9.+-]+""")

/** A type the view still tries to decode when the part names none. */
private const val IMAGE_TYPE = "image/png"
