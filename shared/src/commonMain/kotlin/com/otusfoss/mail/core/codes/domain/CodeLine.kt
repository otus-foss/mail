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
    if (line.text.trim(*FRAMES) != line.text.substring(code.range)) return false
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

/** The tokens of [line] that look like codes, after those that are part of a longer number go. */
private fun candidates(line: String): List<Candidate> =
    runs(TOKEN.findAll(line).toList(), line)
        .filterNot { group -> boxed(group.first().range.first, group.last().range.last, line) }
        .mapNotNull { group ->
            when {
                group.size == 1 -> shape(group.single())
                group.all { it.value.length == 1 } && group.size in SPACED_DIGITS -> joined(group)
                group.size == 2 && evenGroups(group.map { it.value }) -> joined(group)
                else -> null
            }
        }

/** Runs of tokens, where plain digits a single space apart become one run: "482 913", "4 8 2 9 1 3". */
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
private fun joined(group: List<MatchResult>): Candidate =
    Candidate(group.joinToString("") { it.value }, group.first().range.first..group.last().range.last)

/**
 * Whether what stands around the token from [first] to [last] makes it a price, a decimal, a time,
 * a duration, an address, or an id rather than a code: "$482", "482.50", "1440 minutes", "#482913".
 */
private fun boxed(
    first: Int,
    last: Int,
    line: String,
): Boolean {
    val after = line.substring(last + 1)
    return line.getOrNull(first - 1)?.let { it in BEFORE_NUMBER } == true ||
        after.firstOrNull()?.let { it in AFTER_NUMBER } == true ||
        DECIMAL_AFTER.containsMatchIn(after.take(2)) ||
        DURATION.containsMatchIn(after)
}

/** The code [token]'s shape makes: 4 to 8 digits, "482-913", "G-482913", or a code of letters. */
private fun shape(token: MatchResult): Candidate? {
    val text = token.value
    val parts = text.split('-')
    val digits =
        when {
            text.isDigits() -> text.takeIf { it.length in DIGITS && !YEAR.matches(it) }
            evenGroups(parts) -> parts.joinToString("")
            else -> googleCode(parts)
        }
    return when {
        digits != null -> Candidate(digits, token.range)
        text.isDigits() || parts.all { it.isDigits() } -> null
        else -> letterCode(token, parts)
    }
}

/** Whether [groups] are two groups of digits of the same length, 3 or 4: "482 913", "4829-1374". */
private fun evenGroups(groups: List<String>): Boolean =
    groups.size == 2 &&
        groups.all { it.isDigits() } &&
        groups.map { it.length }.distinct().singleOrNull() in SPLIT_GROUPS

/** The digits of a code Google writes as "G-482913". */
private fun googleCode(parts: List<String>): String? =
    parts.getOrNull(1)?.takeIf { parts.size == 2 && parts[0] == "G" && it.isDigits() && it.length in DIGITS }

/**
 * A code of letters such as "K7M2QX", "DJZ-TLX", or "ZKSRHMMHZO": letters and digits mixed, two
 * groups of capitals, or 6 to 10 capitals; never a word in lowercase, a model name, or a commit hash.
 * Two groups of capitals are no booking's reference, which has no dash.
 */
private fun letterCode(
    token: MatchResult,
    parts: List<String>,
): Candidate? {
    val text = token.value
    val plain = text.filter { it.isLetterOrDigit() }
    val upperPair = parts.size == 2 && parts.all { part -> part.length in UPPER_GROUP && part.all { it in 'A'..'Z' } }
    val capitals = parts.size == 1 && plain.length in CAPITALS && plain.all { it in 'A'..'Z' }
    val mixed = plain.any { it.isDigit() } && plain.any { it.isLetter() }
    val fits = plain.length in LETTER_CODE && parts.size <= 2 && !LOWER_RUN.containsMatchIn(text)
    return Candidate(text, token.range, reference = !upperPair).takeIf { fits && (mixed || upperPair || capitals) }
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
private val SPLIT_GROUPS = 3..4
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
private val DECIMAL_AFTER = Regex("""^[.,:]\d""")

/** A unit of time after a number, which makes it how long a code lasts: "1440 minutes", "10 Minuten". */
private val DURATION =
    Regex(
        """^ ?(?:sec|secs|seconds?|min|mins|minutes?|hours?|days?|sekunden|minuten|stunden|tage|""" +
            """secondes|heures|jours|segundos|minutos|horas|días|dias|seconden|uur|dagen)\b""",
        RegexOption.IGNORE_CASE,
    )

/** The end of a sentence, which a code and the word naming it never stand across. */
private val SENTENCE_END = Regex("""[.!?]\s""")

/** Characters that make the number after them a hash, an address, money, a path, or a parameter. */
private const val BEFORE_NUMBER = "#@\$€£¥₹+/\\=&_%"

/** Characters that make the number before them part of an address, a percentage, money, or a path. */
private const val AFTER_NUMBER = "@%€\$£¥_/"

private const val INVISIBLE = "\u00AD\u200B\u200C\u200D\u2060\u2061\u2062\u2063\u2064\uFEFF"
private const val DASHES = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFE63\uFF0D"

/** What may frame a code alone on its line: quotes, brackets, a colon, a full stop. */
private val FRAMES = charArrayOf(' ', '"', '\'', '“', '”', '«', '»', '(', ')', '[', ']', '*', ':', '.')
