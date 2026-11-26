package com.otusfoss.mail.core.messages.data

/**
 * Whether a tag's [attributes] hide its element: a `hidden` attribute, or a `style` that sets
 * `display: none` or `visibility: hidden`. Mail hides preview lines and tracking text that way,
 * which a reader of the text should not see either. Other attributes and properties that merely
 * contain those words, such as `overflow: hidden` or `class="hidden"`, hide nothing.
 */
internal fun hidesElement(attributes: String): Boolean =
    ATTRIBUTE.findAll(attributes).any { attribute ->
        val name = attribute.groupValues[1].lowercase()
        val value =
            attribute.groupValues
                .drop(2)
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        name == "hidden" || (name == "style" && hidingStyle(value))
    }

/** Whether a `style` attribute's [style] declares `display: none` or `visibility: hidden`. */
private fun hidingStyle(style: String): Boolean =
    style
        .split(';')
        .map { declaration -> declaration.split(':', limit = 2).map { it.trim().lowercase() } }
        .filter { it.size == 2 }
        .any { (property, value) -> HIDING.any { (hiding, start) -> property == hiding && value.startsWith(start) } }

/**
 * The index after the end tag that closes the hidden element [name] whose content starts at
 * [from], counting the elements of the same name opened inside it and skipping comments; null when
 * no end tag closes it, as when markup leaves it open.
 */
internal fun String.hiddenElementEnd(
    from: Int,
    name: String,
): Int? {
    var depth = 1
    var open = indexOf('<', from)
    var end: Int? = null
    while (open >= 0 && end == null) {
        val next = if (startsWith(COMMENT_START, open)) indexOf(COMMENT_END, open) else tagEnd(open + 1)
        if (next >= 0) depth += sameTagStep(open, next, name)
        if (depth == 0) end = next
        open = if (next < 0) -1 else indexOf('<', next)
    }
    return end
}

/** How the tag from [open] to [end] changes the depth of [name]: +1 opens one, -1 closes one, 0 neither. */
private fun String.sameTagStep(
    open: Int,
    end: Int,
    name: String,
): Int {
    val closing = getOrNull(open + 1) == '/'
    val nameStart = open + if (closing) 2 else 1
    val same =
        regionMatches(nameStart, name, 0, name.length, ignoreCase = true) &&
            getOrNull(nameStart + name.length)?.isLetterOrDigit() != true
    return when {
        !same -> 0
        closing -> -1
        substring(open, end).trimEnd('>', ' ').endsWith('/') -> 0
        else -> 1
    }
}

/** An attribute: its name, then its value in double quotes, single quotes, or none. */
private val ATTRIBUTE = Regex("""([^\s"'=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")

/** The style declarations that hide an element, by property and the start of its value. */
private val HIDING = listOf("display" to "none", "visibility" to "hidden")
