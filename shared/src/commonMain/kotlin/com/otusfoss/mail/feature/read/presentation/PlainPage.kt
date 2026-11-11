package com.otusfoss.mail.feature.read.presentation

/**
 * A plain-text message as the Read view lays it out: the text the sender wrote in [chunks], and
 * the [quoteLevels] that fold under it, none when it quotes nothing.
 */
class PlainPage(
    val chunks: List<String>,
    val quoteLevels: List<QuoteLevelText>,
)

/** [text] split at its quote, with the part before it in chunks and the quote in levels. */
fun plainPage(text: String): PlainPage {
    val quote = splitPlainQuote(text)
    return PlainPage(textChunks(quote?.body ?: text), quote?.levels().orEmpty())
}

/**
 * [text] in pieces of whole lines, each under [CHUNK_CHARS] unless one line alone is longer, so a
 * long message such as a CI log lays out only the pieces on screen. Joined by line breaks, the
 * pieces give [text] back.
 */
fun textChunks(text: String): List<String> =
    buildList {
        // A loop with a running length: building each piece by copying the last would be quadratic.
        var start = 0
        var length = 0
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            if (index > start && length + line.length >= CHUNK_CHARS) {
                add(lines.subList(start, index).joinToString("\n"))
                start = index
                length = 0
            }
            length += line.length + 1
        }
        add(lines.subList(start, lines.size).joinToString("\n"))
    }

/** Text up to about this many characters lays out in one piece, quickly enough to scroll smoothly. */
private const val CHUNK_CHARS = 4_000
