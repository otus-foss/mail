package com.otusfoss.mail.core.messages.data

/**
 * Whether a tag's [attributes] hide its element, as `display:none`, `visibility:hidden`, and the
 * `hidden` attribute do. Mail hides preview lines and tracking text that way, which a reader of the
 * text should not see either.
 */
internal fun hidesElement(attributes: String): Boolean {
    val plain = attributes.lowercase().filterNot { it.isWhitespace() }
    return HIDDEN_ATTRIBUTE.containsMatchIn(attributes) || HIDDEN_STYLES.any { it in plain }
}

/**
 * The index after the end tag that closes the hidden element [name] whose content starts at
 * [from], counting the elements of the same name opened inside it; the end when none does.
 */
internal fun String.hiddenElementEnd(
    from: Int,
    name: String,
): Int {
    var depth = 1
    var open = indexOf('<', from)
    while (open >= 0) {
        val closing = getOrNull(open + 1) == '/'
        val nameStart = open + if (closing) 2 else 1
        val nameEnd = nameStart + name.length
        val same =
            regionMatches(nameStart, name, 0, name.length, ignoreCase = true) &&
                getOrNull(nameEnd)?.isLetterOrDigit() != true
        if (same) depth += if (closing) -1 else 1
        if (depth == 0) return tagEnd(nameEnd)
        open = indexOf('<', open + 1)
    }
    return length
}

private val HIDDEN_ATTRIBUTE = Regex("""(?:^|\s)hidden(?:[\s=/]|$)""", RegexOption.IGNORE_CASE)
private val HIDDEN_STYLES = listOf("display:none", "visibility:hidden")
