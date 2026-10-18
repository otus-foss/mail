package com.otus.mail.feature.inbox.presentation.read

/**
 * A plain-text reply split for the Read view's quote fold: the [body] the sender wrote, the
 * [header] above the quote when it names who wrote it, and the [quoted] text without its `>` marks.
 */
data class PlainQuote(
    val body: String,
    val header: QuoteHeader?,
    val quoted: String,
)

/**
 * [text] split where it starts quoting an earlier message: at an "On … wrote:" line, also wrapped
 * over two lines, an Outlook "-----Original Message-----" line, or `>` lines that run to the end.
 * Null when it quotes nothing, when `>` lines are mixed with replies, or when nothing comes before
 * the quote, so the message is never hidden.
 */
fun splitPlainQuote(text: String): PlainQuote? {
    val lines = text.lines()
    val start = lines.indices.firstNotNullOfOrNull { lines.quoteAt(it) } ?: return null
    val body = lines.take(start.index).joinToString("\n").trimEnd()
    val quoted =
        lines
            .drop(start.contentFrom)
            .map { it.replaceFirst(MARK, "") }
            .dropWhile(String::isBlank)
            .joinToString("\n")
            .trimEnd()
    return PlainQuote(body, start.header, quoted).takeIf { body.isNotBlank() && quoted.isNotBlank() }
}

/** Where a quote starts: its first line, its [header], and the line its text starts on. */
private class QuoteStart(
    val index: Int,
    val header: QuoteHeader?,
    val contentFrom: Int,
)

private fun List<String>.quoteAt(index: Int): QuoteStart? {
    val line = this[index].trim()
    val header = attributionHeader(line)
    val wrappedHeader = getOrNull(index + 1)?.let { attributionHeader("$line ${it.trim()}") }
    return when {
        line.isEmpty() -> null
        header != null -> QuoteStart(index, header, index + 1)
        wrappedHeader != null -> QuoteStart(index, wrappedHeader, index + 2)
        ORIGINAL.matches(line) -> originalAt(index)
        line.startsWith('>') && drop(index).all(::isMarkedOrBlank) -> QuoteStart(index, null, index)
        else -> null
    }
}

/** An Outlook quote: the separator, then header fields up to the first other line. */
private fun List<String>.originalAt(index: Int): QuoteStart {
    val fields = drop(index + 1).takeWhile(::isHeaderField)
    return QuoteStart(index, fieldsHeader(fields), index + 1 + fields.size)
}

private fun isMarkedOrBlank(line: String): Boolean = line.isBlank() || line.trimStart().startsWith('>')

private val MARK = Regex("""^\s?>\s?""")
private val ORIGINAL =
    Regex("""^-{2,}\s*(?:Original Message|Ursprüngliche Nachricht)\s*-{2,}$""", RegexOption.IGNORE_CASE)
