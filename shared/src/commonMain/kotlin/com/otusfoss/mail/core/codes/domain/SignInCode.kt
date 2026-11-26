package com.otusfoss.mail.core.codes.domain

/** A one-time code a message gives to sign in or to confirm an address. [value] is what the user types. */
data class SignInCode(
    val value: String,
) {
    /** How the code reads: six digits as two groups of three, eight as two groups of four. */
    val shown: String
        get() =
            when {
                value.any { !it.isDigit() } -> value
                value.length == SIX -> "${value.take(SIX / 2)} ${value.drop(SIX / 2)}"
                value.length == EIGHT -> "${value.take(EIGHT / 2)} ${value.drop(EIGHT / 2)}"
                else -> value
            }
}

/**
 * A message to look for a code in: its [subject] and each of its body [texts], such as the plain
 * part and the HTML part read as text, each checked on its own. [fromList] is set for mail a
 * mailing list sent, which never carries a code.
 */
class CodeMail(
    val subject: String,
    val texts: List<String> = emptyList(),
    val fromList: Boolean = false,
)

/**
 * The one code [mail] gives, or null when it gives none or more than one. A code counts only beside
 * a word that names it ("Your code is 482913", "482913 is your Instagram code", "Enter 482913"), or
 * alone on its line under or over such a word, as styled codes come out as text. A reply or a
 * forward has no code of its own. A wrong code is worse than none, so any doubt gives null.
 */
fun findSignInCode(mail: CodeMail): SignInCode? {
    if (mail.fromList || REPLY.containsMatchIn(mail.subject)) return null
    val sources = (listOf(mail.subject) + mail.texts).map(::codeLines)
    val labeled = sources.any { lines -> lines.any { it.words.any(Word::names) } }
    if (!labeled) return null
    return sources
        .flatMap { lines -> lines.indices.flatMap { codesAt(lines, it) } }
        .distinctBy(::codeKey)
        .singleOrNull()
        ?.let(::SignInCode)
}

/** The codes [lines] at [index] gives, beside a word on its line or alone under or over one. */
private fun codesAt(
    lines: List<CodeLine>,
    index: Int,
): List<String> {
    val line = lines[index]
    return line.candidates
        .filter { line.besideLabel(it) || lines.aloneUnderLabel(index, it) }
        .map { it.value }
}

/** Codes that differ only in case or a dash are one code. */
private fun codeKey(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }

/** A reply or a forward, in the prefixes common mail apps write. */
private val REPLY = Regex("""^\s*(?:re|fw|fwd|aw|wg|tr|rv|res|enc|sv|vs|antw)\s*:""", RegexOption.IGNORE_CASE)

private const val SIX = 6
private const val EIGHT = 8
