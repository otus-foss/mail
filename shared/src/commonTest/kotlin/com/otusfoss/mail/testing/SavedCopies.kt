package com.otusfoss.mail.testing

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailBodyValue
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.feature.compose.data.htmlContent
import com.otusfoss.mail.feature.compose.data.textContent
import com.otusfoss.mail.feature.compose.data.toDraft

/** This draft as the server stores it, with the text and HTML Write saves. */
internal fun Draft.savedEmail(): EmailObject = savedEmail(textContent(), htmlContent())

/** A stored copy of this draft's headers with [text] and, when not null, [html] as its body. */
internal fun Draft.savedEmail(
    text: String,
    html: String?,
): EmailObject {
    val plain = EmailBodyPart(partId = "t", type = "text/plain")
    val formatted = EmailBodyPart(partId = "h", type = "text/html")
    return EmailObject(
        id = emailId ?: "d1",
        receivedAt = "2026-11-10T10:00:00Z",
        from = listOfNotNull(from?.let { EmailAddress(it.name, it.address) }),
        subject = subject,
        textBody = listOf(plain),
        htmlBody = listOf(if (html == null) plain else formatted),
        bodyValues = listOfNotNull("t" to EmailBodyValue(text), html?.let { "h" to EmailBodyValue(it) }).toMap(),
    )
}

/** This draft saved and loaded again, split for Write as a reopened draft is. */
internal fun Draft.reopened(): Draft = savedEmail().toDraft(accountId, whole = false)
