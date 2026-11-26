package com.otusfoss.mail.core.codes.domain

/** The tokens of [line] that look like codes, after those that are part of a longer number go. */
internal fun candidates(line: String): List<Candidate> =
    runs(TOKEN.findAll(line).toList(), line)
        .filterNot { group -> boxed(group.first().range.first, group.last().range.last, line) }
        .mapNotNull { group ->
            when {
                group.size == 1 -> shape(group.single())
                group.all { it.value.length == 1 } && group.size in DIGITS -> joined(group)
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
        groups.map { it.length }.distinct().singleOrNull() in GROUP

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
    val upperPair = parts.size == 2 && parts.all { part -> part.length in GROUP && part.all { it in 'A'..'Z' } }
    val capitals = parts.size == 1 && plain.length in CAPITALS && plain.all { it in 'A'..'Z' }
    val mixed = plain.any { it.isDigit() } && plain.any { it.isLetter() }
    val fits = plain.length in LETTER_CODE && parts.size <= 2 && !LOWER_RUN.containsMatchIn(text)
    return Candidate(text, token.range, reference = !upperPair).takeIf { fits && (mixed || upperPair || capitals) }
}

private fun String.isDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

private const val FEWEST_DIGITS = 4
private const val MOST_DIGITS = 8
private const val SMALL_GROUP = 3
private const val LARGE_GROUP = 4
private const val FEWEST_LETTERS = 5
private const val FEWEST_CAPITALS = 6
private const val MOST_LETTERS = 10

/** How many digits a code has, alone or a space apart. */
private val DIGITS = FEWEST_DIGITS..MOST_DIGITS

/** How long each of two groups is: digits in "482 913", capitals in "DJZ-TLX". */
private val GROUP = SMALL_GROUP..LARGE_GROUP
private val LETTER_CODE = FEWEST_LETTERS..MOST_LETTERS
private val CAPITALS = FEWEST_CAPITALS..MOST_LETTERS

private val TOKEN = Regex("""[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*""")
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

/** Characters that make the number after them a hash, an address, money, a path, or a parameter. */
private const val BEFORE_NUMBER = "#@\$€£¥₹+/\\=&_%"

/** Characters that make the number before them part of an address, a percentage, money, or a path. */
private const val AFTER_NUMBER = "@%€\$£¥_/"
