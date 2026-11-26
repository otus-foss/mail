package com.otusfoss.mail.core.codes.domain

/**
 * A token that looks like a code: its [value] to type and where it stands on its line. A
 * [reference] has letters as a booking's reference can, such as "ABC123", so a word about a booking
 * or a confirmation does not name it. A code of capitals alone counts only on a line of its own
 * ([ownLine]), since "Use code SUMMER" is a promotion.
 */
internal class Candidate(
    val value: String,
    val range: IntRange,
    val reference: Boolean = false,
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
 * Whether [code] stands beside a word on its line that names it, a few words before or after it in
 * the same sentence with no other kind of number named between, or just after a word asking to
 * type it. A reference needs a word that names a code; "Enter ABC123" can be a booking.
 */
internal fun CodeLine.besideLabel(code: Candidate): Boolean =
    !code.ownLine && words.any { word -> namesBeside(word, code) || asksBefore(word, code) }

/** Whether [word] names [code] from before it or after it on this line. */
private fun CodeLine.namesBeside(
    word: Word,
    code: Candidate,
): Boolean =
    when {
        !word.namesCandidate(code) -> false
        word.range.last < code.range.first -> clearBetween(word.range.last + 1, code.range.first, LABEL_GAP)
        code.range.last < word.range.first -> clearBetween(code.range.last + 1, word.range.first, LABEL_AFTER_GAP)
        else -> false
    }

/** Whether [word] asks to type [code] just before it: "Enter 482913". */
private fun CodeLine.asksBefore(
    word: Word,
    code: Candidate,
): Boolean =
    word.command &&
        !code.reference &&
        word.range.last < code.range.first &&
        clearBetween(word.range.last + 1, code.range.first, COMMAND_GAP)

private fun Word.namesCandidate(code: Candidate): Boolean = if (code.reference) namesLetterCode else names

/**
 * Whether [code] stands alone on the line at [index], with the nearest of the two lines above or
 * the line below naming it, as a code set in large type under "Verification code" comes out.
 */
internal fun List<CodeLine>.aloneUnderLabel(
    index: Int,
    code: Candidate,
): Boolean {
    val line = this[index]
    if (line.text.trim { it in FRAMES } != line.text.substring(code.range)) return false
    val near = listOfNotNull(getOrNull(index - 1), getOrNull(index - 2)).firstOrNull { it.labels(code) != null }
    return near?.labels(code) == true || getOrNull(index + 1)?.labels(code) == true
}

/**
 * True when this line names [code], even beside words such as "card" or "phone" that sign-in mail
 * uses too; false when it names only another kind of number; null when it names neither.
 */
private fun CodeLine.labels(code: Candidate): Boolean? =
    when {
        words.any { it.namesCandidate(code) } -> true
        words.any { it.warns } -> false
        else -> null
    }

/**
 * Whether the text from [start] until [end] is at most [gap] words in one sentence, names no other
 * kind of number, and holds no other code.
 */
private fun CodeLine.clearBetween(
    start: Int,
    end: Int,
    gap: Int,
): Boolean {
    if (end < start || end - start > MAX_GAP_CHARS || SENTENCE_END.containsMatchIn(text.substring(start, end))) {
        return false
    }
    val between = start until end
    return words.count { it.range.first in between } <= gap &&
        words.none { it.warns && it.range.first in between } &&
        candidates.none { it.range.first in between }
}

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

private val URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
private val SPACES = Regex(""" {2,}""")

/** The end of a sentence, which a code and the word naming it never stand across. */
private val SENTENCE_END = Regex("""[.!?]\s""")

private const val INVISIBLE = "\u00AD\u200B\u200C\u200D\u2060\u2061\u2062\u2063\u2064\uFEFF"
private const val DASHES = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFE63\uFF0D"

/** What may frame a code alone on its line: quotes, brackets, a colon, a full stop. */
private const val FRAMES = " \"'“”«»()[]*:."
