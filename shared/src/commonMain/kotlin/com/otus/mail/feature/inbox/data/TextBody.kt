package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.displayText

/**
 * The open message's text and HTML bodies. The text loses hidden characters, and an HTML-only body
 * becomes plain text. A part cut off before another part's text ends with [GAP_NOTE], so the gap
 * shows where it is; a cut at the end sets [MessageContent.truncated] instead.
 */
internal fun EmailObject.toMessageContent(): MessageContent {
    val values = textBodyValues()
    val lastText = values.indexOfLast { it.value.isNotBlank() }
    val text =
        values.withIndex().joinToString("\n") { (index, part) ->
            if (part.isTruncated && index < lastText) "${part.value}\n$GAP_NOTE" else part.value
        }
    return MessageContent(
        text = text.displayText(),
        truncated = values.drop(maxOf(lastText, 0)).any { it.isTruncated },
        html = toMessageHtml(),
    )
}

/**
 * The value of each [EmailObject.textBody] part the server sent, HTML as readable text. The text
 * body holds the HTML part when a message has no plain alternative (RFC 8621, section 4.1.4).
 */
private fun EmailObject.textBodyValues(): List<EmailBodyValue> =
    textBody.orEmpty().mapNotNull { part ->
        part.partId?.let { bodyValues[it] }?.let { if (part.isHtml) it.copy(value = it.value.htmlToText()) else it }
    }

/** Marks where a cut-off part stops when more of the body follows it. */
private const val GAP_NOTE = "[Part of the message is missing here.]"
