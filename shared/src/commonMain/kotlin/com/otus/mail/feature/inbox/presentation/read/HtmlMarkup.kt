package com.otus.mail.feature.inbox.presentation.read

/**
 * The visible text of a short piece of HTML, such as a quote's header: breaks and block ends become
 * line breaks, other tags go, and common character references are decoded. Styles, titles, and the
 * head are not text, so their content goes too. Full message bodies go
 * through the data layer's reader instead. Each step reads the markup once, so a whole message, or
 * one full of `<` without a `>`, takes time in line with its length.
 */
internal fun markupText(html: String): String =
    withoutTags(withoutHidden(html).replace(LINE_BREAK, "\n"))
        .let { text -> ENTITIES.entries.fold(text) { acc, (entity, char) -> acc.replace(entity, char) } }
        .lines()
        .map { it.replace(SPACES, " ").trim() }
        .filter(String::isNotEmpty)
        .joinToString("\n")

/**
 * Where the [tag] element opening at [start] ends: the start of its matching end tag and the index
 * after it. A missing end tag runs the element to the end of [html].
 */
internal fun elementEnd(
    html: String,
    start: Int,
    tag: String,
): Pair<Int, Int> {
    var depth = 0
    tagsNamed(html, tag, start).forEach { found ->
        depth += if (found.closing) -1 else 1
        if (depth == 0) return found.start to found.end
    }
    return html.length to html.length
}

/** The content between the opening tag at [start] and [closeStart]; empty when no `>` ends that tag. */
internal fun innerHtml(
    html: String,
    start: Int,
    closeStart: Int,
): String {
    val tagEnd = html.indexOf('>', start)
    return if (tagEnd < 0) "" else html.substring((tagEnd + 1).coerceAtMost(closeStart), closeStart)
}

/** [html] without its tags, each from a `<` to the first `>` after it, as `<[^>]*>` removes them. */
private fun withoutTags(html: String): String =
    buildString {
        var from = 0
        while (from < html.length) {
            val open = html.indexOf('<', from)
            val close = if (open < 0) -1 else html.indexOf('>', open)
            val textEnd = if (close < 0) html.length else open
            appendRange(html, from, textEnd)
            from = if (close < 0) html.length else close + 1
        }
    }

/**
 * [html] without its head, styles, titles, and scripts, as `<(name)\b[^>]*>[\s\S]*?</\1\s*>` removes them.
 * An element whose end tag never comes stays, and so do the later ones of its name.
 */
private fun withoutHidden(html: String): String {
    val unclosed = mutableSetOf<String>()
    return buildString {
        var from = 0
        var index = html.indexOf('<')
        while (index >= 0) {
            val end = hiddenEnd(html, index, unclosed)
            if (end != null) {
                appendRange(html, from, index)
                from = end
            }
            index = html.indexOf('<', end ?: (index + 1))
        }
        appendRange(html, from, html.length)
    }
}

/**
 * Where the hidden element starting at [index] ends, or null when none starts there. A name whose end tag
 * never comes goes into [unclosed], so no later element of that name is searched for again.
 */
private fun hiddenEnd(
    html: String,
    index: Int,
    unclosed: MutableSet<String>,
): Int? {
    val name = HIDDEN_ENDS.keys.firstOrNull { it !in unclosed && html.hasTagAt(index, it) } ?: return null
    val tagEnd = html.indexOf('>', index)
    val close = if (tagEnd < 0) null else HIDDEN_ENDS.getValue(name).find(html, tagEnd + 1)
    if (close == null) unclosed += name
    return close?.range?.last?.plus(1)
}

private val LINE_BREAK = Regex("""<br\s*/?>|</(?:p|div|tr|li|h\d)>""", RegexOption.IGNORE_CASE)
private val HIDDEN_ENDS =
    listOf("head", "style", "title", "script").associateWith { Regex("""</$it\s*>""", RegexOption.IGNORE_CASE) }
private val SPACES = Regex("""[ \t ]+""")
private val ENTITIES =
    mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&#64;" to "@",
        "&nbsp;" to " ",
        "&amp;" to "&",
    )
