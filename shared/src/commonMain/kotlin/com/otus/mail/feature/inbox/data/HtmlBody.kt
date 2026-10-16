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

/** An image, background, or stylesheet address on another server; a plain link does not count. */
private val REMOTE_RESOURCE =
    Regex("""(?i)(?:\bsrc|srcset|background)\s*=\s*["']?\s*(?:https?:)?//|url\(\s*["']?\s*(?:https?:)?//|<link\b""")
