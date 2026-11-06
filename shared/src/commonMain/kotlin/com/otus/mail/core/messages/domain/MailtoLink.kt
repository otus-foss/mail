package com.otus.mail.core.messages.domain

/**
 * The new message a `mailto:` [link] asks for (RFC 6068): the addresses before `?` and in `to`
 * fields go to To, and `cc`, `bcc`, `subject`, and `body` fields fill in the rest. Field names
 * ignore case, and values are percent-decoded as UTF-8; `+` stays a plus sign. Repeated address
 * fields add up, while the first `subject` and `body` count. Other fields, such as `from` or
 * `in-reply-to`, are left out, as section 6 advises for headers a link should not set. Null when
 * [link] is not a `mailto:` URI.
 */
fun mailtoRequest(link: String): ComposeRequest.New? {
    val scheme = link.substringBefore(':', missingDelimiterValue = "")
    if (!scheme.trim().equals(MAILTO, ignoreCase = true)) return null
    val target = link.substringAfter(':').substringBefore('#')
    val fields =
        target
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .filter { it.isNotEmpty() }
            .map(::mailtoField)
    return ComposeRequest.New(
        to = parseRecipients(target.substringBefore('?').percentDecoded()) + fields.recipients(TO),
        cc = fields.recipients(CC),
        bcc = fields.recipients(BCC),
        subject = fields.firstValue(SUBJECT).orEmpty(),
        body = fields.firstValue(BODY)?.withPlainLineBreaks().orEmpty(),
    )
}

/** One `name=value` pair of a link's query, its [value] still percent-encoded. */
private class MailtoField(
    val name: String,
    val value: String,
)

private fun mailtoField(text: String): MailtoField =
    MailtoField(text.substringBefore('=').percentDecoded(), text.substringAfter('=', missingDelimiterValue = ""))

private fun List<MailtoField>.decodedValues(name: String): List<String> =
    filter { it.name.equals(name, ignoreCase = true) }.map { it.value.percentDecoded() }

private fun List<MailtoField>.recipients(name: String): List<MailSender> =
    decodedValues(name).flatMap(::parseRecipients)

private fun List<MailtoField>.firstValue(name: String): String? = decodedValues(name).firstOrNull()

/**
 * The text with each run of `%XX` escapes read as UTF-8 bytes, so an escaped character of several
 * bytes decodes whole. A malformed sequence becomes the replacement character; a lone `%` stays.
 */
internal fun String.percentDecoded(): String =
    ESCAPES.replace(this) { escaped ->
        escaped.value
            .chunked(ESCAPE_LENGTH)
            .map { it.drop(1).toInt(HEX).toByte() }
            .toByteArray()
            .decodeToString()
    }

/** A body's CRLF line breaks, which RFC 6068 requires, as the editor's plain line breaks. */
private fun String.withPlainLineBreaks(): String = replace("\r\n", "\n").replace('\r', '\n')

private const val MAILTO = "mailto"
private const val TO = "to"
private const val CC = "cc"
private const val BCC = "bcc"
private const val SUBJECT = "subject"
private const val BODY = "body"
private const val ESCAPE_LENGTH = 3
private const val HEX = 16
private val ESCAPES = Regex("(?:%[0-9A-Fa-f]{2})+")
