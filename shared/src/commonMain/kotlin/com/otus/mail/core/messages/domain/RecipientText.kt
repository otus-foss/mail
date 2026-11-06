package com.otus.mail.core.messages.domain

/**
 * The people a field such as To holds, written as the user types them: addresses or
 * `Name <address>`, separated by commas or semicolons. Commas inside quotes or angle brackets
 * do not separate.
 */
fun parseRecipients(text: String): List<MailSender> = splitRecipients(text).mapNotNull(::parseRecipient)

/** The field text for these people, which [parseRecipients] reads back. */
fun List<MailSender>.recipientText(): String = joinToString(", ") { it.recipientText() }

/** One person as `Name <address>`, or the bare address when the name is blank or the same. */
fun MailSender.recipientText(): String {
    val shown = name.trim()
    return when {
        shown.isEmpty() || shown.equals(address, ignoreCase = true) -> address
        shown.any { it in NEEDS_QUOTES } -> "\"${shown.replace("\"", "")}\" <$address>"
        else -> "$shown <$address>"
    }
}

private fun parseRecipient(part: String): MailSender? {
    val open = part.lastIndexOf('<')
    val close = part.lastIndexOf('>')
    if (open >= 0 && close > open) {
        val address = part.substring(open + 1, close).trim()
        val name =
            part
                .substring(0, open)
                .trim()
                .trim('"')
                .trim()
        return address.takeIf { it.isNotEmpty() }?.let { MailSender(name, it) }
    }
    return part
        .trim()
        .trim('"')
        .takeIf { it.isNotEmpty() }
        ?.let { MailSender("", it) }
}

private fun splitRecipients(text: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    var quoted = false
    var bracketed = false

    fun finishPart(index: Int) {
        parts += text.substring(start, index)
        start = index + 1
    }
    text.forEachIndexed { index, char ->
        when {
            char == '"' -> quoted = !quoted
            char == '<' && !quoted -> bracketed = true
            char == '>' && !quoted -> bracketed = false
            char in SEPARATORS && !quoted && !bracketed -> finishPart(index)
        }
    }
    parts += text.substring(start)
    return parts.map(String::trim).filter(String::isNotEmpty)
}

private const val SEPARATORS = ",;"
private const val NEEDS_QUOTES = ",;<>\""
