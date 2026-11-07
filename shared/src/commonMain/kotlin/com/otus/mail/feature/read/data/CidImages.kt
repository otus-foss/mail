package com.otus.mail.feature.read.data

import com.otus.jmap.JmapException
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.messages.domain.MessageContent
import kotlin.io.encoding.Base64

/**
 * Resolves a bounded set of CID images without allowing repeated references to expand the
 * document beyond its memory budget. Missing or oversized images keep their original addresses.
 */
internal suspend fun MailAccount.withInlineImages(
    content: MessageContent,
    parts: List<EmailBodyPart>,
): MessageContent {
    val html = content.html?.takeIf { it.html.length <= MAX_HTML_CHARS } ?: return content
    val images = inlineImages(html.html, parts)
    return if (images.isEmpty()) {
        content
    } else {
        val inlined = CID_URI.replace(html.html) { match -> images[match.groupValues[1].lowercase()] ?: match.value }
        content.copy(html = html.copy(html = inlined))
    }
}

private suspend fun MailAccount.inlineImages(
    html: String,
    parts: List<EmailBodyPart>,
): Map<String, String> {
    val references = CID_URI.findAll(html).groupingBy { it.groupValues[1].lowercase() }.eachCount()
    var remainingBytes = MAX_TOTAL_INLINE_BYTES
    var remainingChars = MAX_HTML_CHARS.toLong() - html.length
    return buildMap {
        parts.asSequence().distinctBy { it.cid?.lowercase() }.take(MAX_INLINE_PARTS).forEach { part ->
            val cid = part.cid?.lowercase() ?: return@forEach
            val count = references[cid] ?: return@forEach
            val limit = minOf(MAX_INLINE_BYTES, remainingBytes)
            if (limit <= 0 || part.size !in 0..limit) return@forEach
            val type = part.type?.lowercase()?.takeIf(IMAGE_MEDIA_TYPE::matches) ?: IMAGE_TYPE
            val prefix = "data:$type;base64,"
            val estimated = prefix.length + base64Length(part.size)
            if (estimated * count > remainingChars) return@forEach

            // Downloads run one at a time to bound peak memory, so a finished download is charged
            // at its real size. A blob refused as too large was read up to the limit, so it costs that.
            val bytes =
                try {
                    inlineBytes(part, type, limit)
                } catch (expected: JmapException.ResponseTooLarge) {
                    remainingBytes -= limit
                    null
                } catch (expected: JmapException) {
                    null
                } ?: return@forEach
            remainingBytes -= bytes.size
            val encodedLength = prefix.length + base64Length(bytes.size.toLong())
            val added = encodedLength * count
            if (added > remainingChars) return@forEach
            remainingChars -= added
            put(cid, prefix + Base64.encode(bytes))
        }
    }
}

private suspend fun MailAccount.inlineBytes(
    part: EmailBodyPart,
    type: String,
    maxBytes: Long,
): ByteArray? =
    part.blobId?.let { blobId -> connection.download(id, blobId, type, maxBytes, part.name ?: "image").bytes }

private const val MAX_INLINE_BYTES = 5L * 1024 * 1024
private const val MAX_TOTAL_INLINE_BYTES = 8L * 1024 * 1024
private const val MAX_HTML_CHARS = 12 * 1024 * 1024
private const val MAX_INLINE_PARTS = 32

/** The type is inserted into an HTML attribute, so keep only a plain image media type. */
private val IMAGE_MEDIA_TYPE = Regex("""image/[a-z0-9.+-]+""")
private const val IMAGE_TYPE = "image/png"

private fun base64Length(bytes: Long): Long = ((bytes + BASE64_BYTES - 1) / BASE64_BYTES) * BASE64_CHARS

private const val BASE64_BYTES = 3
private const val BASE64_CHARS = 4
