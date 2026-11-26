package com.otusfoss.mail.core.codes.domain

/**
 * A word on a line, lowercase, at [range] of the line. [names] when it names a one-time code, such
 * as "code", "OTP", or "Bestätigungscode". [namesLetterCode] when it may also name one with letters,
 * which a booking's or a confirmation's reference often is. [command] when it asks to type a code,
 * as "Enter" does, and [warns] when it names another kind of number, such as an order's.
 */
internal class Word(
    val text: String,
    val range: IntRange,
    val names: Boolean,
    val namesLetterCode: Boolean,
    val command: Boolean,
    val warns: Boolean,
)

/** The words of [line] at their places, each judged with the words around it. */
internal fun words(line: String): List<Word> {
    val found = WORD.findAll(line).toList()
    val texts = found.map { it.value.lowercase() }
    return found.mapIndexed { index, match ->
        val text = texts[index]
        val near = Near(texts.getOrNull(index - 1), texts.drop(index + 1).take(WORDS_AFTER))
        val names = namesCode(text, near)
        Word(
            text = text,
            range = match.range,
            names = names,
            namesLetterCode = names && !doubtsLetters(text, near),
            command = text in COMMANDS,
            warns = text in WARNINGS || WARNING_ENDINGS.any(text::endsWith),
        )
    }
}

/** The word before a word, and the two after it. */
private class Near(
    val before: String?,
    val after: List<String>,
)

/** Whether [word] names a one-time code where it stands, and not a postcode, a promo code, or a QR code. */
private fun namesCode(
    word: String,
    near: Near,
): Boolean =
    when {
        word in LABELS -> true
        word == "password" || word == "passwort" -> near.before == "time" || near.before == "einmal"
        codePrefix(word) == null || word in NOT_CODE_WORDS -> false
        else -> codePrefix(word) !in NOT_CODES && near.before !in NOT_CODES && !qualifiedAfter(near, NOT_CODES)
    }

/** Whether a code [word] names may be a booking's or a confirmation's reference, when it has letters. */
private fun doubtsLetters(
    word: String,
    near: Near,
): Boolean =
    codePrefix(word) in LETTER_DOUBTS ||
        near.before in LETTER_DOUBTS ||
        qualifiedAfter(near, LETTER_DOUBTS)

/** What comes before "code" in [word]: "" for "code", "bestätigungs" for "Bestätigungscode"; null without one. */
private fun codePrefix(word: String): String? = CODE_ENDINGS.firstOrNull(word::endsWith)?.let { word.removeSuffix(it) }

/** "code postal", "código de descuento": a word of [words] after the code word, or after its "de". */
private fun qualifiedAfter(
    near: Near,
    words: Set<String>,
): Boolean {
    val next = near.after.firstOrNull()
    return next in words || (next in LINKS && near.after.getOrNull(1) in words)
}

private val WORD = Regex("""\p{L}+""")

/** How many words after a code word can say what kind of code it is: "código de descuento". */
private const val WORDS_AFTER = 2
