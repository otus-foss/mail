package com.otus.mail.feature.folders.data

/**
 * [script] with [block] as its Otus block, at its end, and [require] as the block's `require`
 * line, after the script's leading `require` commands (RFC 5228, section 3.2). At the end, the
 * block's `stop` (section 3.3) ends only what the app adds: the script's own commands all run
 * first. A block found anywhere else, as at the top where the app used to put it, moves to the
 * end, and its old `require` line goes. Nothing else in the script changes, byte for byte.
 */
internal fun spliceBlock(
    script: String,
    block: String,
    require: String? = null,
): String {
    val rest = withoutRequire(withoutBlock(script))
    val required = if (require == null) rest else withRequire(rest, require)
    // A last line without a line end needs one before the block.
    val separator = if (required.isEmpty() || required.endsWith('\n')) "" else CRLF
    return required + separator + block
}

private fun withoutBlock(script: String): String =
    findBlock(script)?.let { script.removeRange(it.start, it.end) } ?: script

/** [script] without the `require` line the app wrote for its block. */
private fun withoutRequire(script: String): String {
    val start = lineStarts(script).firstOrNull { script.lineAt(it).isOwnRequire() } ?: return script
    val newline = script.indexOf('\n', start)
    return script.removeRange(start, if (newline < 0) script.length else newline + 1)
}

private fun String.isOwnRequire(): Boolean = startsWith("$REQUIRE ") && endsWith(" $REQUIRE_MARK")

/** [script] with [require] after its leading `require` commands, or at the top without them. */
private fun withRequire(
    script: String,
    require: String,
): String {
    val at = afterLeadingRequires(script)
    // A last require line without a line end needs one before the block's.
    val separator = if (at > 0 && !script.substring(0, at).endsWith('\n')) CRLF else ""
    return script.substring(0, at) + separator + require + script.substring(at)
}
