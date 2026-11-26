package com.otusfoss.mail.feature.read.data

import com.otusfoss.mail.core.codes.domain.CodeMail
import com.otusfoss.mail.core.codes.domain.SignInCode
import com.otusfoss.mail.core.codes.domain.findSignInCode
import com.otusfoss.mail.core.mail.EmailBodyValue
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.htmlToText
import com.otusfoss.mail.core.messages.data.isHtml
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.displayText

/**
 * The open message's text and HTML bodies. The text loses hidden characters, and an HTML-only body
 * becomes plain text. A part cut off before another part's text ends with [GAP_NOTE], so the gap
 * shows where it is; a cut at the end sets [MessageContent.truncated] instead. The files leave out
 * the images the HTML shows, and the list fields come from the message's list headers.
 */
internal fun EmailObject.toMessageContent(): MessageContent {
    val values = textBodyValues()
    val lastText = values.indexOfLast { it.value.isNotBlank() }
    val text =
        values.withIndex().joinToString("\n") { (index, part) ->
            if (part.isTruncated && index < lastText) "${part.value}\n$GAP_NOTE" else part.value
        }
    val html = toMessageHtml()
    val shown = text.displayText()
    return MessageContent(
        text = shown,
        truncated = values.drop(maxOf(lastText, 0)).any { it.isTruncated },
        html = html,
        attachments = files(html?.html),
        unsubscribe = unsubscribe(),
        listName = listName(listId),
        signInCode = signInCode(shown, html?.html),
    )
}

/**
 * The one-time code the message gives, from its subject, its text, and its HTML read as text, each
 * checked on its own. The HTML is read again only when the text is not already it.
 */
private fun EmailObject.signInCode(
    text: String,
    html: String?,
): SignInCode? {
    val htmlText = html?.takeUnless { textBody.orEmpty().any { it.isHtml } }?.htmlToText()
    return findSignInCode(CodeMail(subject.orEmpty(), listOfNotNull(text, htmlText), fromList = listId != null))
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
