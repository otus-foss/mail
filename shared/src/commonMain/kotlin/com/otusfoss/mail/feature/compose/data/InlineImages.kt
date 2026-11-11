package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.mail.MailAccount
import kotlin.io.encoding.Base64

/**
 * Uploads the `data:` image each `<img>` element of [html] shows (RFC 8620, section 6.1) and points
 * the element at its part with a `cid:` URI instead, so the message carries the image rather than
 * linking to it (RFC 2392). Only an element's `src` changes: the same text anywhere else, as in a
 * quote, stays as written, and so does a `data:` URI that is not valid base64. [uploads] reuses an
 * image uploaded before. Null [html] stays null.
 */
internal suspend fun MailAccount.inlineImages(
    html: String?,
    uploads: InlineImageUploads,
): EmailHtml? = html?.let { uploaded(it, uploads) }

private suspend fun MailAccount.uploaded(
    html: String,
    uploads: InlineImageUploads,
): EmailHtml {
    val found =
        imageSource
            .findAll(html)
            .map { it.groupValues[SOURCE] }
            .distinct()
            .mapNotNull(::dataImage)
            .toList()
    val images =
        found.mapIndexed { index, image ->
            val upload = uploads.upload(this, image)
            image.uri to InlineImage("image$index@otus.mail", upload.blobId, upload.type.ifBlank { image.type })
        }
    val cids = images.associate { (uri, image) -> uri to "cid:${image.cid}" }
    val rewritten =
        imageSource.replace(html) { match ->
            val (start, quote, source) = match.destructured
            cids[source]?.let { cid -> start + cid + quote } ?: match.value
        }
    return EmailHtml(rewritten, images.map { it.second })
}

/** A `data:` image [uri] and the [bytes] of [type] it holds. */
internal class DataImage(
    val uri: String,
    val type: String,
    val bytes: ByteArray,
)

/** The image [uri] holds, or null when it is no base64 `data:` URI of an image. */
private fun dataImage(uri: String): DataImage? {
    val (type, data) = dataUri.matchEntire(uri)?.destructured ?: return null
    return decoded(data.filterNot(Char::isWhitespace))?.let { DataImage(uri, type, it) }
}

private fun decoded(base64: String): ByteArray? =
    try {
        lenientBase64.decode(base64)
    } catch (expected: IllegalArgumentException) {
        null
    }

private val lenientBase64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

/**
 * An `<img>` element up to the quote that opens its `src` attribute, that quote, and the `data:`
 * URI it holds.
 */
private val imageSource = Regex("""(<img\b[^>]*?\ssrc\s*=\s*(["']))(data:[^"'>]*)\2""", RegexOption.IGNORE_CASE)

private const val SOURCE = 3

/** A base64 `data:` URI of an image type; base64 may be wrapped over lines. */
private val dataUri = Regex("""data:(image/[\w+.-]+);base64,([A-Za-z0-9+/=\s]+)""", RegexOption.IGNORE_CASE)
