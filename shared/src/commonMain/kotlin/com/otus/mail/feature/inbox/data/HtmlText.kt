package com.otus.mail.feature.inbox.data

/**
 * Readable plain text from an HTML body part, which the app shows and quotes as text and never
 * renders. Drops what a browser does not show: comments, declarations, and the content of script,
 * style, template, and title elements, which leaves nothing of the head. Blocks and `<br>` break
 * lines, other tags go, and character references are decoded. It reads the markup once, so
 * malformed or cut-off markup costs no more than its length; a tag left open at the end is dropped.
 */
internal fun String.htmlToText(): String {
    val text = PlainText()
    var index = 0
    while (index < length) {
        val char = this[index]
        index =
            when {
                char == '<' && startsWith(COMMENT_START, index) -> {
                    commentEnd(index)
                }

                char == '<' && opensTag(index + 1) -> {
                    tag(index, text)
                }

                char == '&' -> {
                    reference(index, text)
                }

                else -> {
                    text.append(char)
                    index + 1
                }
            }
    }
    return text.toString()
}

/** Whether a tag starts after a `<` at [index]; otherwise the `<` is text, as in `a < b`. */
private fun String.opensTag(index: Int): Boolean =
    getOrNull(index)?.let { it in 'a'..'z' || it in 'A'..'Z' || it in "/!?" } == true

/** Lays out the tag at [start] and returns where text resumes, after the content of a hidden element. */
private fun String.tag(
    start: Int,
    text: PlainText,
): Int {
    val closing = this[start + 1] == '/'
    val nameStart = if (closing) start + 2 else start + 1
    val nameEnd = (nameStart until length).firstOrNull { !this[it].isLetterOrDigit() } ?: length
    val name = substring(nameStart, nameEnd).lowercase()
    val end = tagEnd(nameEnd)
    when (name) {
        "br" -> text.lineBreak()
        in PARAGRAPHS -> text.paragraph()
        in LINES -> text.line()
        in CELLS -> text.space()
    }
    return if (!closing && name in HIDDEN) hiddenContentEnd(end, name) else end
}

/** The index after the `>` that closes a tag, skipping quoted attribute values; the end when none does. */
private fun String.tagEnd(from: Int): Int {
    var quote: Char? = null
    var valueStarts = false
    for (index in from until length) {
        val char = this[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else if (char == '>') {
            return index + 1
        } else if (valueStarts && (char == '"' || char == '\'')) {
            quote = char
        }
        valueStarts = quote == null && (char == '=' || (valueStarts && char.isWhitespace()))
    }
    return length
}

/** The index after the end tag of the hidden element [name] whose content starts at [from], or the end. */
private fun String.hiddenContentEnd(
    from: Int,
    name: String,
): Int {
    val endTag = "</$name"
    var index = indexOf(endTag, from, ignoreCase = true)
    // `</styles>` does not end a style element; a space, `/`, `>`, or the end must follow the name.
    while (index >= 0 && getOrNull(index + endTag.length)?.let { it.isWhitespace() || it in "/>" } == false) {
        index = indexOf(endTag, index + 1, ignoreCase = true)
    }
    return if (index < 0) length else tagEnd(index + endTag.length)
}

/** The index after the comment at [start], which `-->` closes, also as in `<!-->`; the end when none does. */
private fun String.commentEnd(start: Int): Int =
    indexOf(COMMENT_END, start + 2).let { if (it < 0) length else it + COMMENT_END.length }

/** Appends the character reference at [start], or `&` itself when there is none, and returns where text resumes. */
private fun String.reference(
    start: Int,
    text: PlainText,
): Int {
    val reference = if (getOrNull(start + 1) == '#') numericReference(start) else namedReference(start)
    val (decoded, end) = reference ?: ("&" to start + 1)
    decoded.forEach(text::append)
    return end
}

/** A named reference such as `&amp;`. Only a known name that ends in `;` counts, so `&b=2` in a link stays. */
private fun String.namedReference(start: Int): Pair<String, Int>? {
    val end =
        (start + 1..minOf(lastIndex, start + LONGEST_NAME + 1))
            .firstOrNull { !this[it].isLetterOrDigit() }
            ?.takeIf { this[it] == ';' } ?: return null
    return NAMED_REFERENCES[substring(start + 1, end)]?.let { it to end + 1 }
}

/** A numeric reference such as `&#233;` or `&#xE9;`, which browsers also read without the `;`. */
private fun String.numericReference(start: Int): Pair<String, Int>? {
    val hex = getOrNull(start + 2)?.lowercaseChar() == 'x'
    val radix = if (hex) HEX else DECIMAL
    val digitsStart = start + if (hex) "&#x".length else "&#".length
    val digitsEnd = (digitsStart until length).firstOrNull { this[it].digitToIntOrNull(radix) == null } ?: length
    if (digitsEnd == digitsStart) return null
    val digits = substring(digitsStart, digitsEnd).trimStart('0')
    val code = digits.takeIf { it.length <= MAX_DIGITS }?.toIntOrNull(radix) ?: 0
    return codePointText(code) to if (getOrNull(digitsEnd) == ';') digitsEnd + 1 else digitsEnd
}

/**
 * The character a code point names, as in browsers: U+FFFD for zero, a surrogate, or a value beyond
 * Unicode, and the Windows-1252 character for 128 to 159, so `&#146;` is an apostrophe.
 */
private fun codePointText(code: Int): String =
    when {
        code == 0 || code > MAX_CODE_POINT || code in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code -> {
            "\uFFFD"
        }

        code in WINDOWS_1252_START until WINDOWS_1252_START + WINDOWS_1252.length -> {
            WINDOWS_1252[code - WINDOWS_1252_START].toString()
        }

        code < SUPPLEMENTARY -> {
            Char(code).toString()
        }

        else -> {
            val offset = code - SUPPLEMENTARY
            val high = Char.MIN_HIGH_SURROGATE + (offset shr SURROGATE_BITS)
            val low = Char.MIN_LOW_SURROGATE + (offset and SURROGATE_MASK)
            charArrayOf(high, low).concatToString()
        }
    }

/**
 * Text as a browser lays it out: each run of whitespace is one space, no line starts or ends with
 * one, and breaks never leave more than one blank line.
 */
private class PlainText {
    private val text = StringBuilder()
    private var space = false
    private var breaks = 0

    fun append(char: Char) {
        if (char.isWhitespace()) {
            space()
        } else {
            if (space) text.append(' ')
            text.append(char)
            space = false
            breaks = 0
        }
    }

    /** A space before the next text, unless that text starts a line. */
    fun space() {
        space = text.isNotEmpty() && breaks == 0
    }

    /** A `<br>`: one more line break, short of a second blank line. */
    fun lineBreak() = breakTo(minOf(breaks + 1, 2))

    /** The edge of a block such as a list item: the next text starts a new line. */
    fun line() = breakTo(1)

    /** The edge of a paragraph-like block: a blank line before the next text. */
    fun paragraph() = breakTo(2)

    private fun breakTo(count: Int) {
        if (text.isEmpty()) return
        repeat(count - breaks) { text.append('\n') }
        breaks = maxOf(breaks, count)
        space = false
    }

    override fun toString() = text.toString().trimEnd()
}

private const val COMMENT_START = "<!--"
private const val COMMENT_END = "-->"
private const val DECIMAL = 10
private const val HEX = 16
private const val MAX_DIGITS = 8
private const val MAX_CODE_POINT = 0x10FFFF
private const val SUPPLEMENTARY = 0x10000
private const val SURROGATE_BITS = 10
private const val SURROGATE_MASK = 0x3FF
private const val WINDOWS_1252_START = 0x80

/** What browsers read numeric references 128 to 159 as; the five unassigned ones stay control characters. */
private const val WINDOWS_1252 =
    "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
        "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"

/** Elements whose content a browser never shows as text. */
private val HIDDEN = setOf("script", "style", "template", "title")

/** Blocks set apart by a blank line, as their margins are in a browser. */
private val PARAGRAPHS = setOf("blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "ol", "p", "pre", "table", "ul")

/** Blocks that start on a new line and end one. */
private val LINES =
    setOf(
        "address",
        "article",
        "aside",
        "center",
        "dd",
        "div",
        "dl",
        "dt",
        "footer",
        "form",
        "header",
        "li",
        "section",
        "tr",
    )

/** Table cells, kept apart by a space. */
private val CELLS = setOf("td", "th")

/** The named references mail commonly uses; a no-break space reads as a plain space. */
private val NAMED_REFERENCES =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "shy" to "",
        "zwnj" to "\u200C",
        "zwj" to "\u200D",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "deg" to "\u00B0",
        "times" to "\u00D7",
        "middot" to "\u00B7",
        "bull" to "\u2022",
        "hellip" to "\u2026",
        "ndash" to "\u2013",
        "mdash" to "\u2014",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
        "euro" to "\u20AC",
        "pound" to "\u00A3",
        "cent" to "\u00A2",
        "yen" to "\u00A5",
    )

private val LONGEST_NAME = NAMED_REFERENCES.keys.maxOf { it.length }
