package com.otusfoss.mail.feature.folders.data

/** A string list (RFC 5228, section 2.4.2.1). */
internal fun sieveList(values: List<String>): String = values.joinToString(", ", "[", "]") { sieveString(it) }

/**
 * A quoted string (RFC 5228, section 2.4.2): backslashes and quotes are escaped. NUL and lone CR
 * or LF are not allowed in one, so control characters other than tab are left out.
 */
internal fun sieveString(value: String): String =
    "\"" + value.withoutControls().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Text compared with `:matches` treats `*`, `?`, and `\` literally once escaped. */
internal fun literalWildcards(value: String): String =
    value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?")

/** Comments and header values hold one line; line breaks would end a comment early. */
internal fun oneLine(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").withoutControls()

private fun String.withoutControls(): String = filterNot { it < ' ' && it != '\t' }
