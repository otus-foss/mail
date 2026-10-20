package com.otus.mail.feature.inbox.presentation.read

/**
 * The visible text of a short piece of HTML, such as a quote's header: breaks and block ends become
 * line breaks, other tags go, and common character references are decoded. Styles, titles, and the
 * head are not text, so their content goes too. Full message bodies go
 * through the data layer's reader instead; this only has to read a line or two.
 */
internal fun markupText(html: String): String =
    html
        .replace(HIDDEN, "")
        .replace(LINE_BREAK, "\n")
        .replace(TAG, "")
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
    Regex("""<(/?)$tag\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html, start).forEach { found ->
        depth += if (found.groupValues[1].isEmpty()) 1 else -1
        if (depth == 0) return found.range.first to found.range.last + 1
    }
    return html.length to html.length
}

/** The content between the opening tag at [start] and [closeStart]. */
internal fun innerHtml(
    html: String,
    start: Int,
    closeStart: Int,
): String = html.substring((html.indexOf('>', start) + 1).coerceAtMost(closeStart), closeStart)

private val LINE_BREAK = Regex("""<br\s*/?>|</(?:p|div|tr|li|h\d)>""", RegexOption.IGNORE_CASE)
private val TAG = Regex("""<[^>]*>""")
private val HIDDEN = Regex("""<(head|style|title|script)\b[^>]*>[\s\S]*?</\1\s*>""", RegexOption.IGNORE_CASE)
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
