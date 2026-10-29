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
fun splitPlainQuote(text: String): PlainQuote? = splitQuote(text, nested = false)

/** One level of a quote: its [header], when it names who wrote it, and only its own [text]. */
data class QuoteLevelText(
    val header: QuoteHeader?,
    val text: String,
)

/**
 * This quote and the quotes nested in it, outermost first, up to [MAX_LEVELS], each with only its
 * own text and without its `>` marks. A level quoted in a way the split cannot read stays part of
 * the text of the level around it.
 */
fun PlainQuote.levels(): List<QuoteLevelText> {
    val quotes = generateSequence(this) { splitQuote(it.quoted, nested = true) }.take(MAX_LEVELS).toList()
    return quotes.mapIndexed { index, quote ->
        QuoteLevelText(quote.header, quotes.getOrNull(index + 1)?.body ?: quote.quoted)
    }
}

/**
 * A quote's [text] under its [header], as the levels it holds, outermost first, such as the text
 * of an HTML quote that quotes earlier mail as plain `>` lines or "On … wrote:" lines.
 */
fun quoteLevels(
    header: QuoteHeader?,
    text: String,
): List<QuoteLevelText> = PlainQuote("", header, text).levels()

/** [text] split at its quote; a [nested] quote may start at its first line, with nothing before it. */
private fun splitQuote(
    text: String,
    nested: Boolean,
): PlainQuote? {
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
    return PlainQuote(body, start.header, quoted).takeIf { (nested || body.isNotBlank()) && quoted.isNotBlank() }
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

/**
 * An Outlook quote: the separator, then header fields up to the first other line. Without fields,
 * an "On … wrote:" line right after it names the quote instead, as Proton Mail writes it.
 */
private fun List<String>.originalAt(index: Int): QuoteStart {
    val fields = drop(index + 1).takeWhile(::isHeaderField)
    val next = (index + 1 until size).firstOrNull { this[it].isNotBlank() }
    val attribution = next?.takeIf { fields.isEmpty() }?.let(::quoteAt)?.takeIf { it.header != null }
    return attribution?.let { QuoteStart(index, it.header, it.contentFrom) }
        ?: QuoteStart(index, fieldsHeader(fields), index + 1 + fields.size)
}

private fun isMarkedOrBlank(line: String): Boolean = line.isBlank() || line.trimStart().startsWith('>')

private val MARK = Regex("""^\s?>\s?""")
private val ORIGINAL = Regex("^$ORIGINAL_MESSAGE$", RegexOption.IGNORE_CASE)

/** The separator line Outlook and Proton Mail write above a quoted message, in English or German. */
internal const val ORIGINAL_MESSAGE = """-{2,}\s*(?:Original Message|Ursprüngliche Nachricht)\s*-{2,}"""

/** How many quote levels a reply shows apart; deeper ones stay in the text of the last. */
private const val MAX_LEVELS = 8
