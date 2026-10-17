package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.RuleSupport

/** The rules Otus keeps in its block, by folder, and the UTC offset its day and time conditions use. */
internal data class RuleBlock(
    val folders: List<BlockFolder>,
    val zone: String = UTC,
)

/** The rules read from a block. [changedElsewhere] when its Sieve no longer matches its JSON line. */
internal data class ReadBlock(
    val block: RuleBlock,
    val changedElsewhere: Boolean,
)

internal const val BLOCK_BEGIN = "# BEGIN otus-rules"
internal const val BLOCK_END = "# END otus-rules"
private const val NOTE = "# Otus Mail writes this block. Change these rules in Otus Mail; edits here are replaced."
private const val METADATA_PREFIX = "# otus-rules-v2 "
private const val CRLF = "\r\n"

/**
 * The whole block, from [BLOCK_BEGIN] to [BLOCK_END] with CRLF line ends. Its own `require` comes
 * first inside it, and the JSON line before that lets the app read the rules back.
 */
internal fun encodeBlock(
    block: RuleBlock,
    support: RuleSupport,
): String =
    buildList {
        add(BLOCK_BEGIN)
        add(NOTE)
        add(METADATA_PREFIX + blockJson(block))
        addAll(sieveLines(block.folders, support, block.zone))
        add(BLOCK_END)
    }.joinToString(CRLF, postfix = CRLF)

/**
 * Reads the rules from a block's text. The Sieve is regenerated from its JSON and compared, so an
 * edit made in another app is reported instead of being silently replaced. A block without a
 * readable JSON line yields no rules and counts as changed elsewhere.
 */
internal fun decodeBlock(
    text: String,
    support: RuleSupport,
): ReadBlock {
    val block =
        text
            .lineSequence()
            .map { it.trimEnd('\r') }
            .firstOrNull { it.startsWith(METADATA_PREFIX) }
            ?.let { parseBlockJson(it.removePrefix(METADATA_PREFIX)) }
            ?: return ReadBlock(RuleBlock(emptyList()), changedElsewhere = true)
    return ReadBlock(block, normalized(encodeBlock(block, support)) != normalized(text))
}

/** Servers may store other line ends or trailing spaces; neither is an edit. */
private fun normalized(text: String): String = text.lines().joinToString("\n") { it.trimEnd() }.trimEnd()

/** Where the block sits in a script: [start] is its first character, [end] the one after its last line end. */
internal data class BlockSpan(
    val start: Int,
    val end: Int,
)

/** The block in [script], from its begin line to the end of its end line; null when there is none. */
internal fun findBlock(script: String): BlockSpan? {
    val start = lineStarts(script).firstOrNull { script.lineAt(it) == BLOCK_BEGIN }
    val endLine =
        start?.let { begin -> lineStarts(script).firstOrNull { it > begin && script.lineAt(it) == BLOCK_END } }
    val newline = endLine?.let { script.indexOf('\n', it) }
    return when {
        start == null || newline == null -> null
        newline < 0 -> BlockSpan(start, script.length)
        else -> BlockSpan(start, newline + 1)
    }
}

/**
 * [script] with [block] in place of its Otus block. Without one, the block goes after the
 * script's leading `require` commands, because RFC 5228 (section 3.2) allows a `require` only
 * before every other command; a script without them gets the block at the very top. Nothing
 * outside the block changes, byte for byte.
 */
internal fun spliceBlock(
    script: String,
    block: String,
): String {
    val span = findBlock(script)
    if (span != null) return script.substring(0, span.start) + block + script.substring(span.end)
    val at = afterLeadingRequires(script)
    // A last require line without a line end needs one before the block.
    val separator = if (at > 0 && !script.substring(0, at).endsWith('\n')) CRLF else ""
    return script.substring(0, at) + separator + block + script.substring(at)
}

/** The text of the block in [script], or null. */
internal fun blockText(script: String): String? = findBlock(script)?.let { script.substring(it.start, it.end) }

private fun lineStarts(text: String): Sequence<Int> =
    generateSequence(0) { start ->
        text
            .indexOf('\n', start)
            .takeIf { it >= 0 }
            ?.plus(1)
            ?.takeIf { it < text.length }
    }

private fun String.lineAt(start: Int): String {
    val end = indexOf('\n', start).let { if (it < 0) length else it }
    return substring(start, end).trimEnd('\r', ' ', '\t')
}
