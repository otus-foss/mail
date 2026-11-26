package com.otusfoss.mail.core.messages.domain

internal const val MOVE_TO_PREFIX = "move_to:"
internal const val LABEL_PREFIX = "label:"
internal const val GO_TO_PREFIX = "go_to:"
internal const val FILTER_PREFIX = "filter:"
internal const val WRITE_TO_PREFIX = "write_to:"
internal const val CHAIN_PREFIX = "chain:"

/** [parts] as one saved value, such as `Clients/Acme`, with the separators inside a part escaped. */
internal fun encodeParts(parts: List<String>): String = parts.joinToString(PART_SEPARATOR) { it.escaped() }

/** The parts saved after [prefix] in [value]; null when one of them is empty. */
internal fun decodeParts(
    value: String,
    prefix: String,
): List<String>? =
    value
        .removePrefix(prefix)
        .split(PART_SEPARATOR)
        .map { it.unescaped() }
        .takeIf { parts -> parts.none { it.isEmpty() } }

private const val PART_SEPARATOR = "/"

// A part may hold the characters a saved bar and a list of parts use as separators.
private val ESCAPES = mapOf('%' to "%25", '/' to "%2F", ',' to "%2C", '|' to "%7C")

private fun String.escaped(): String = map { ESCAPES[it] ?: it.toString() }.joinToString("")

private fun String.unescaped(): String =
    ESCAPE.replace(this) {
        it.groupValues[1]
            .toInt(HEX)
            .toChar()
            .toString()
    }

private const val HEX = 16
private val ESCAPE = Regex("%([0-9A-Fa-f]{2})")
