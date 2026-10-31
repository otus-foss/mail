package com.otus.mail.feature.inbox.domain

import kotlinx.serialization.Serializable

/** An identity's signature, as JMAP stores it on the Identity: formatted [html] and plain [text]. */
@Serializable
data class Signature(
    val html: String = "",
    val text: String = "",
) {
    val isEmpty: Boolean get() = html.isBlank() && text.isBlank()

    /** The start of the signature on one line, such as "Alex Rivera · Product lead". */
    val preview: String
        get() =
            text
                .lines()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .take(PREVIEW_LINES)
                .joinToString(" · ")

    private companion object {
        const val PREVIEW_LINES = 2
    }
}

/** An identity's signature. */
val SendIdentity.signature: Signature get() = Signature(htmlSignature, textSignature)

/**
 * An image in a signature, kept in its HTML as a `data:` URI of [mediaType] so the identity carries
 * it, and shown [width] pixels wide.
 */
data class SignatureImage(
    val mediaType: String,
    val base64: String,
    val width: Int,
)

/** A signature split for editing: the formatted text, then the images under it. */
data class SignatureParts(
    val textHtml: String = "",
    val images: List<SignatureImage> = emptyList(),
) {
    /** The signature's HTML: the text, then each image on its own line. */
    fun html(): String =
        textHtml +
            images.joinToString("") {
                """<div><img src="data:${it.mediaType};base64,${it.base64}" width="${it.width}" alt=""></div>"""
            }
}

/** Splits signature [html] into its text and the `data:` images Mail added to it. */
fun parseSignature(html: String): SignatureParts {
    val images =
        imagePattern.findAll(html).map { match ->
            val (type, data, width) = match.destructured
            SignatureImage(type, data, width.toInt())
        }
    val text = imageBlockPattern.replace(html, "").replace(imagePattern, "")
    return SignatureParts(text, images.toList())
}

/** The image widths the signature editor offers by name. */
enum class SignatureImageSize(
    val label: String,
    val width: Int,
) {
    Small("Small", SMALL_WIDTH),
    Medium("Medium", MEDIUM_WIDTH),
    Large("Large", LARGE_WIDTH),
}

private const val SMALL_WIDTH = 96
private const val MEDIUM_WIDTH = 132
private const val LARGE_WIDTH = 200

private val imagePattern = Regex("""<img src="data:([\w/+.-]+);base64,([A-Za-z0-9+/=]+)" width="(\d+)"[^>]*>""")
private val imageBlockPattern = Regex("""<div>(<img src="data:[^"]+" width="\d+"[^>]*>)</div>""")
