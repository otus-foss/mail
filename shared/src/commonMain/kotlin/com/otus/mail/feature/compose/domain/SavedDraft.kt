package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.AttachmentBlob
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftAttachment
import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageContent

/**
 * The draft a saved copy in Drafts holds, with its full [content], to edit again. It keeps the
 * copy's [Draft.emailId], so the next save replaces that copy instead of adding one, and its files,
 * which stay on the server as blobs. Write adds the identity's signature again, so the text
 * loses the one the copy was saved with.
 */
fun MailMessage.savedDraft(content: MessageContent): Draft {
    val parts = content.text.savedParts()
    return Draft(
        accountId = location.accountId,
        from = sender,
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        body = parts.body,
        quote = parts.quote,
        inReplyTo = references.takeLast(1),
        references = references,
        emailId = emailId,
        attachments = content.attachments.map { it.draftFile(location.accountId) },
    )
}

/** What the user wrote in a saved draft, and the quote of the message it answers or forwards. */
internal data class SavedText(
    val body: String,
    val quote: String = "",
)

/**
 * Splits a saved draft's plain text, which Write stores as the body, then a signature after a
 * "-- " line, then the quote, each after a blank line (see `Draft.textContent`).
 */
internal fun String.savedParts(): SavedText {
    val lines = lines()
    val signature = lines.indexOf(SIGNATURE_LINE).takeIf { it >= 0 }
    val quote =
        lines.indices
            .drop(signature ?: 0)
            .firstOrNull { it.startsQuote(lines) }
    val bodyEnd = listOfNotNull(signature, quote).minOrNull() ?: lines.size
    return SavedText(
        body = lines.take(bodyEnd).joinToString("\n").trimEnd(),
        quote = quote?.let { lines.drop(it).joinToString("\n").trimEnd() }.orEmpty(),
    )
}

/** Whether line [this] of [lines] opens a reply's quote or a forward, after a blank line. */
private fun Int.startsQuote(lines: List<String>): Boolean =
    lines.getOrNull(this - 1).isNullOrBlank() &&
        (quoteHeader.matches(lines[this]) || lines[this] == FORWARD_HEADER)

private const val SIGNATURE_LINE = "-- "

private val quoteHeader = Regex("^On .+ wrote:$")

private fun MailAttachment.draftFile(accountId: String): DraftAttachment =
    DraftAttachment(blobId, name, type, size, AttachmentBlob(accountId, blobId))
