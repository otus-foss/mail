package com.otus.mail.feature.folders.data

/** A string list (RFC 5228, section 2.4.2.1). */
internal fun sieveList(values: List<String>): String = values.joinToString(", ", "[", "]") { sieveString(it) }

/** A quoted string (RFC 5228, section 2.4.2): backslashes and quotes are escaped. */
internal fun sieveString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Text compared with `:matches` treats `*`, `?`, and `\` literally once escaped. */
internal fun literalWildcards(value: String): String =
    value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?")

/** Comments and header values hold one line; line breaks would end a comment early. */
internal fun oneLine(value: String): String = value.replace(Regex("[\\r\\n]+"), " ")
