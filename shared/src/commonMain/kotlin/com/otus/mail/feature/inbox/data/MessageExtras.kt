package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.displayLine

/**
 * The message's files: its `attachments` (RFC 8621, section 4.1.4) without the images [html]
 * shows by `cid:`, since those render in the body. Parts the server cannot download are left out.
 */
internal fun EmailObject.files(html: String?): List<MailAttachment> {
    val shown = cidReferences(html)
    return attachments
        .orEmpty()
        .filterNot { it.cid?.lowercase() in shown }
        .mapNotNull { part -> part.blobId?.let { part.toAttachment(it) } }
}

/** The `cid:` parts of [html] and its attachments that the HTML refers to, each once. */
internal fun EmailObject.inlineParts(html: String?): List<EmailBodyPart> {
    val shown = cidReferences(html)
    return (htmlBody.orEmpty() + attachments.orEmpty())
        .filter { it.blobId != null && it.cid?.lowercase() in shown }
        .distinctBy { it.cid?.lowercase() }
}

/**
 * Manual unsubscribe routes from `List-Unsubscribe`. A sender-controlled header alone cannot
 * authorize an RFC 8058 POST without trusted DKIM verification of both unsubscribe headers.
 */
internal fun EmailObject.unsubscribe(): Unsubscribe? {
    val urls = listUnsubscribe.orEmpty().map { it.trim() }
    val https = urls.firstOrNull { it.startsWith("https://", ignoreCase = true) }
    val found =
        Unsubscribe(
            web = https ?: urls.firstOrNull { it.startsWith("http://", ignoreCase = true) },
            mail = urls.firstOrNull { it.startsWith("mailto:", ignoreCase = true) },
            deliveredTo =
                (deliveredTo.orEmpty() + originalTo.orEmpty())
                    .flatMap { it.orEmpty() }
                    .map { it.email.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
        )
    return found.takeIf { it.web != null || it.mail != null }
}

/** The list's name from a `List-Id` such as `"Weekly digest" <weekly.example.org>`; null when it has none. */
internal fun listName(listId: String?): String? =
    listId
        ?.substringBefore('<')
        ?.trim()
        ?.trim('"')
        ?.displayLine()
        ?.ifBlank { null }

/** The id inside a `List-Id`'s angle brackets (RFC 2919), such as `weekly.example.org`. */
internal fun listIdentifier(listId: String?): String? =
    listId
        ?.substringAfter('<', missingDelimiterValue = "")
        ?.substringBefore('>')
        ?.trim()
        ?.ifBlank { null }

/** The lowercase Content-Ids [html] refers to as `cid:` URIs (RFC 2392). */
internal fun cidReferences(html: String?): Set<String> =
    html
        ?.let { CID_URI.findAll(it).map { match -> match.groupValues[1].lowercase() }.toSet() }
        .orEmpty()

private fun EmailBodyPart.toAttachment(blobId: String) =
    MailAttachment(
        name = name.orEmpty().displayLine().ifBlank { "Attachment" },
        type = type ?: GENERIC_TYPE,
        size = size,
        blobId = blobId,
    )

/** A `cid:` URI up to the quote, space, or bracket that ends the attribute or CSS value holding it. */
internal val CID_URI = Regex("""cid:([^"'\s)>]+)""", RegexOption.IGNORE_CASE)

private const val GENERIC_TYPE = "application/octet-stream"
