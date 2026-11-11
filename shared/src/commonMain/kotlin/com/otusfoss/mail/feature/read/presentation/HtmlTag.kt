package com.otusfoss.mail.feature.read.presentation

/** A tag found in markup: from its `<` at [start] to after its `>` at [end]; [closing] for an end tag. */
internal class HtmlTag(
    val start: Int,
    val end: Int,
    val closing: Boolean,
)

/**
 * The [name] tags in [html] from [from], start and end tags alike, as `<(/?)name\b[^>]*>` finds them: each runs
 * to the first `>` after it. Each tag is read once, so markup full of `<` without a `>` costs no more than its
 * length.
 */
internal fun tagsNamed(
    html: String,
    name: String,
    from: Int = 0,
): Sequence<HtmlTag> = generateSequence(nextTag(html, name, from)) { nextTag(html, name, it.end) }

/** Where each [name] start tag whose markup holds [attribute], such as `class="gmail_quote"`, starts. */
internal fun startTags(
    html: String,
    name: String,
    attribute: Regex,
    from: Int = 0,
): Sequence<Int> =
    tagsNamed(html, name, from)
        .filter { !it.closing && attribute.containsMatchIn(html.subSequence(it.start, it.end)) }
        .map { it.start }

/**
 * Where a tag that starts as [opening] begins when only whitespace lies between its `>` and [end], as
 * `opening[^>]*>\s*$` finds it in the text up to [end]; null when none ends there.
 */
internal fun startTagBefore(
    html: String,
    end: Int,
    opening: Regex,
): Int? {
    val tagEnd = html.lastNonSpaceBefore(end)
    if (tagEnd < 0 || html[tagEnd] != '>') return null
    val from = html.lastIndexOf('>', tagEnd - 1) + 1
    return opening.find(html.subSequence(from, tagEnd))?.let { it.range.first + from }
}

/**
 * Where an end tag named one of [names] begins, with the whitespace before it, when only whitespace follows it
 * up to [end], as `\s*</(?:names)\s*>\s*$` finds it in the text up to [end]; null when none ends there.
 */
internal fun trailingEndTag(
    html: String,
    end: Int,
    names: List<String>,
): Int? {
    val tagEnd = html.lastNonSpaceBefore(end)
    val nameEnd = html.lastNonSpaceBefore(tagEnd) + 1
    val name =
        names.firstOrNull {
            html.getOrNull(
                tagEnd,
            ) == '>' && html.hasTagAt(nameEnd - it.length - 2, it, closing = true)
        }
    return name?.let { html.lastNonSpaceBefore(nameEnd - it.length - 2) + 1 }
}

/** The first [name] tag from [from], or null when none is left or no `>` ends it. */
private fun nextTag(
    html: String,
    name: String,
    from: Int,
): HtmlTag? {
    var index = html.indexOf('<', from)
    while (index >= 0 && !html.hasTagAt(index, name) && !html.hasTagAt(index, name, closing = true)) {
        index = html.indexOf('<', index + 1)
    }
    // Without a `>` after it, no later tag can end either.
    val tagEnd = if (index < 0) -1 else html.indexOf('>', index)
    return if (tagEnd < 0) null else HtmlTag(index, tagEnd + 1, html.hasTagAt(index, name, closing = true))
}

/** Whether a [name] tag starts at [index], as `<name\b` matches there, or `</name\b` for a [closing] one. */
internal fun String.hasTagAt(
    index: Int,
    name: String,
    closing: Boolean = false,
): Boolean {
    val opening = if (closing) "</" else "<"
    val nameEnd = index + opening.length + name.length
    return index >= 0 &&
        startsWith(opening, index) &&
        regionMatches(index + opening.length, name, 0, name.length, ignoreCase = true) &&
        getOrNull(nameEnd)?.isWordChar() != true
}

/** The index of the last character before [end] that is not whitespace as regex `\s` reads it, or below 0. */
private fun String.lastNonSpaceBefore(end: Int): Int {
    var index = end - 1
    while (index >= 0 && this[index] in SPACE_CHARS) index--
    return index
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'

/** The characters regex `\s` matches. */
private const val SPACE_CHARS = " \t\n\u000B\u000C\r"
