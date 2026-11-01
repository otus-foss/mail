package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MessageHtml

/**
 * The [EmailObject.htmlBody] parts as one HTML document, or null when none of them is HTML, since
 * the text body already shows a plain message. Plain parts between HTML parts keep their line
 * breaks. Parts without a value, such as inline images, are left out.
 */
internal fun EmailObject.toMessageHtml(): MessageHtml? {
    val values = htmlBody.orEmpty().mapNotNull { part -> part.partId?.let(bodyValues::get)?.let { part to it } }
    if (values.none { it.first.isHtml }) return null
    val html =
        values.joinToString("\n") { (part, value) ->
            if (part.isHtml) {
                value.value
            } else {
                "<div style=\"white-space:pre-wrap\">${value.value.escapeHtml()}</div>"
            }
        }
    return MessageHtml(html, REMOTE_RESOURCE.containsMatchIn(html), values.any { it.second.isTruncated })
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * An address on another server that the page would load: an image, video poster, embedded object,
 * background, stylesheet, CSS import, or SVG image. A plain link does not count. An SVG tag is read
 * only up to the next `<`, so a body full of unclosed tags is not searched again from each one.
 */
private val REMOTE_RESOURCE =
    Regex(
        listOf(
            """(?:\bsrc|srcset|background|\bposter|\bdata|xlink:href)\s*=\s*["']?\s*$REMOTE""",
            """<(?:image|use|feImage)\b[^<>]*\shref\s*=\s*["']?\s*$REMOTE""",
            """url\(\s*["']?\s*$REMOTE""",
            """@import\s*["']\s*$REMOTE""",
            """<link\b""",
        ).joinToString("|", prefix = "(?i)"),
    )

private const val REMOTE = """(?:https?:)?//"""
