package com.otusfoss.mail.core.codes.domain

/**
 * A token that looks like a code: its [value] to type, where it stands on its line, and whether it
 * has [letters]. A code of capitals alone counts only on a line of its own, since "Use code SUMMER"
 * beside a word is a promotion.
 */
internal class Candidate(
    val value: String,
    val range: IntRange,
    val letters: Boolean,
) {
    val ownLine: Boolean get() = value.all { it in 'A'..'Z' }
}

/** One non-empty line of a message, with its [words] and the [candidates] that look like codes. */
internal class CodeLine(
    val text: String,
    val words: List<Word>,
    val candidates: List<Candidate>,
)

/**
 * The non-empty lines of [text], its first [MAX_CHARS] characters, as codes see them: any script's
 * digits read as 0 to 9, every space and dash is a plain one, and invisible characters and links go.
 */
internal fun codeLines(text: String): List<CodeLine> =
    URL
        .replace(text.take(MAX_CHARS), " ")
        .map(::plainChar)
        .joinToString("")
        .lines()
        .map { SPACES.replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
        .map { CodeLine(it, words(it), candidates(it)) }

/**
 * Whether [code] stands beside a word on its line that names it, a few words before or after it
 * with no other kind of number named between, or just after a word asking to type it. A code with
 * letters needs a word that names a code; "Enter ABC123" can be a booking.
 */
internal fun CodeLine.besideLabel(code: Candidate): Boolean =
    !code.ownLine &&
        words.any { word ->
            val names = if (code.letters) word.namesLetterCode else word.names
            when {
                names && word.range.last < code.range.first -> {
                    clearBetween(word.range.last + 1, code.range.first, LABEL_GAP)
                }

                names -> {
                    clearBetween(code.range.last + 1, word.range.first, LABEL_AFTER_GAP)
                }

                word.command && !code.letters && word.range.last < code.range.first -> {
                    clearBetween(word.range.last + 1, code.range.first, COMMAND_GAP)
                }

                else -> {
                    false
                }
            }
        }

/**
 * Whether [code] stands alone on the line at [index], with the nearest of the two lines above or
 * the line below naming it, as a code set in large type under "Verification code" comes out.
 */
internal fun List<CodeLine>.aloneUnderLabel(
    index: Int,
    code: Candidate,
): Boolean {
    val line = this[index]
    if (line.text.trim(*FRAMES) != line.text.substring(code.range)) return false
    val near = listOfNotNull(getOrNull(index - 1), getOrNull(index - 2)).firstOrNull { it.labels(code) != null }
    return near?.labels(code) == true || getOrNull(index + 1)?.labels(code) == true
}

/** True when this line names [code], false when it names another kind of number, null when it does neither. */
private fun CodeLine.labels(code: Candidate): Boolean? =
    when {
        words.any { it.warns } -> false
        words.any { if (code.letters) it.namesLetterCode else it.names } -> true
        else -> null
    }

/** Whether the text from [start] until [end] is at most [gap] words, names no other number, and holds no code. */
private fun CodeLine.clearBetween(
    start: Int,
    end: Int,
    gap: Int,
): Boolean {
    if (end - start > MAX_GAP_CHARS) return false
    val between = start until end
    return words.count { it.range.first in between } <= gap &&
        words.none { it.warns && it.range.first in between } &&
        candidates.none { it.range.first in between }
}

/** The tokens of [line] that look like codes, after those that are part of a longer number go. */
private fun candidates(line: String): List<Candidate> =
    runs(TOKEN.findAll(line).toList(), line).mapNotNull { run ->
        when {
            run.size == 1 -> candidate(run.single(), line)
            run.all { it.value.length == 1 } && run.size in SPACED_DIGITS -> joined(run)
            run.size == 2 && run.all { it.value.length == SPLIT_GROUP } -> joined(run)
            else -> null
        }
    }

/** Runs of tokens, where runs of plain digits a single space apart become one run: "482 913", "4 8 2 9 1 3". */
private fun runs(
    tokens: List<MatchResult>,
    line: String,
): List<List<MatchResult>> =
    tokens.fold(emptyList()) { runs, token ->
        val last = runs.lastOrNull()?.last()
        if (last != null && digitsApart(last, token, line)) {
            runs.dropLast(1) + listOf(runs.last() + token)
        } else {
            runs + listOf(listOf(token))
        }
    }

/** Whether [first] and [second] are plain digits with one space between them on [line]. */
private fun digitsApart(
    first: MatchResult,
    second: MatchResult,
    line: String,
): Boolean =
    first.value.isDigits() &&
        second.value.isDigits() &&
        line.substring(first.range.last + 1, second.range.first) == " "

/** Digits a run joins into one code, as typed without the spaces. */
private fun joined(run: List<MatchResult>): Candidate =
    Candidate(run.joinToString("") { it.value }, run.first().range.first..run.last().range.last, letters = false)

/** The code [token] is, unless what stands around it makes it a price, a date, an address, or an id. */
private fun candidate(
    token: MatchResult,
    line: String,
): Candidate? {
    val before = line.getOrNull(token.range.first - 1)
    val after = line.substring(token.range.last + 1).take(2)
    val boxed =
        before?.let { it in BEFORE_NUMBER } == true ||
            after.firstOrNull()?.let { it in AFTER_NUMBER } == true ||
            DECIMAL_AFTER.matches(after)
    return if (boxed) null else shape(token)
}

/** The code [token]'s shape makes: 4 to 8 digits, "482-913", "G-482913", or 5 to 10 letters and digits mixed. */
private fun shape(token: MatchResult): Candidate? {
    val text = token.value
    val parts = text.split('-')
    val digits =
        when {
            text.isDigits() -> text.takeIf { it.length in DIGITS && !YEAR.matches(it) }
            parts.size != 2 -> null
            parts.all { it.isDigits() && it.length == SPLIT_GROUP } -> parts.joinToString("")
            parts[0] == "G" && parts[1].isDigits() && parts[1].length in DIGITS -> parts[1]
            else -> null
        }
    return when {
        digits != null -> Candidate(digits, token.range, letters = false)
        text.isDigits() || parts.all { it.isDigits() } -> null
        else -> letterCode(text, parts)?.let { Candidate(it, token.range, letters = true) }
    }
}

/**
 * A code of letters such as "K7M2QX", "DJZ-TLX", or "ZKSRHMMHZO": letters and digits mixed, two
 * groups of capitals, or 6 to 10 capitals; never a word in lowercase, a model name, or a commit hash.
 */
private fun letterCode(
    text: String,
    parts: List<String>,
): String? {
    val plain = text.filter { it.isLetterOrDigit() }
    val upperPair = parts.size == 2 && parts.all { part -> part.length in UPPER_GROUP && part.all { it in 'A'..'Z' } }
    val capitals = parts.size == 1 && plain.length in CAPITALS && plain.all { it in 'A'..'Z' }
    val mixed = plain.any { it.isDigit() } && plain.any { it.isLetter() }
    val fits = plain.length in LETTER_CODE && parts.size <= 2 && !LOWER_RUN.containsMatchIn(text)
    return text.takeIf { fits && (mixed || upperPair || capitals) }
}

private fun String.isDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

/** [char] as codes read it: any script's digit as 0 to 9, spaces and dashes plain, invisible characters gone. */
private fun plainChar(char: Char): String =
    when {
        char in '0'..'9' -> char.toString()
        char.isDigit() -> char.digitToIntOrNull()?.toString() ?: ""
        char in INVISIBLE -> ""
        char == '\n' -> "\n"
        char.isWhitespace() -> " "
        char in DASHES -> "-"
        char == '\uFF1A' -> ":"
        else -> char.toString()
    }

private const val MAX_CHARS = 20_000
private const val MAX_GAP_CHARS = 40
private const val LABEL_GAP = 4
private const val LABEL_AFTER_GAP = 5
private const val COMMAND_GAP = 2
private const val SPLIT_GROUP = 3
private val DIGITS = 4..8
private val SPACED_DIGITS = 4..8
private val LETTER_CODE = 5..10
private val UPPER_GROUP = 3..4
private val CAPITALS = 6..10

private val TOKEN = Regex("""[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*""")
private val URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
private val SPACES = Regex(""" {2,}""")
private val YEAR = Regex("""(?:19|20)\d\d""")
private val LOWER_RUN = Regex("""[a-z]{3}""")

/** A decimal point or a time's colon after a number: "1234.56", "1234,5", "10:30". */
private val DECIMAL_AFTER = Regex("""[.,:]\d""")

/** Characters that make the number after them a hash, an address, money, a path, or a parameter. */
private const val BEFORE_NUMBER = "#@\$€£¥₹+/\\=&_%"

/** Characters that make the number before them part of an address, a percentage, money, or a path. */
private const val AFTER_NUMBER = "@%€\$£¥_/"

private const val INVISIBLE = "\u00AD\u200B\u200C\u200D\u2060\u2061\u2062\u2063\u2064\uFEFF"
private const val DASHES = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFE63\uFF0D"

/** What may frame a code alone on its line: quotes, brackets, a colon, a full stop. */
private val FRAMES = charArrayOf(' ', '"', '\'', '“', '”', '«', '»', '(', ')', '[', ']', '*', ':', '.')
