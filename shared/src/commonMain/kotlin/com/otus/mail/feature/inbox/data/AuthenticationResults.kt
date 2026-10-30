package com.otus.mail.feature.inbox.data

/**
 * The domain of [fromAddress] when the topmost `Authentication-Results` field in [fields] (RFC 8601)
 * says the message passed DMARC for it, else null. The receiving server adds the topmost field, and
 * a sender can add fields further down, so only that one counts, and only when its server id names
 * [serverHost], the user's JMAP server, or another host of its domain. A server that adds no field
 * would otherwise leave the sender's own on top. A DMARC result other than a pass, or a pass for
 * another From domain, counts as none.
 */
internal fun dmarcPassDomain(
    fields: List<String>?,
    fromAddress: String,
    serverHost: String,
): String? {
    val domain = fromAddress.substringAfterLast('@', "").trim().lowercase()
    val parts = fields?.firstOrNull()?.let(::fieldParts).orEmpty()
    val serverId =
        parts
            .firstOrNull()
            ?.let(::words)
            ?.firstOrNull()
            .orEmpty()
    if (domain.isEmpty() || !isOwnServer(serverId, serverHost)) return null
    val dmarc = parts.drop(1).map(::words).filter { it.first().startsWith("dmarc=", ignoreCase = true) }
    val passed =
        dmarc.isNotEmpty() &&
            dmarc.all { words ->
                words.first().equals("dmarc=pass", ignoreCase = true) &&
                    words.drop(1).any { it.equals("header.from=$domain", ignoreCase = true) }
            }
    return domain.takeIf { passed }
}

/** Whether [serverId] names [serverHost] or another host of its domain, such as `mx` beside `mail`. */
private fun isOwnServer(
    serverId: String,
    serverHost: String,
): Boolean {
    val id = serverId.lowercase().trimEnd('.')
    val host = serverHost.lowercase()
    val parent = host.substringAfter('.').takeIf { '.' in it } ?: host
    return host.isNotEmpty() && (id == host || id == parent || id.endsWith(".$parent"))
}

/**
 * The `;`-separated parts of one field, the server id first. Comments are left out, and a `;`
 * inside a comment or quoted string does not split, since both can hold text the sender chose,
 * such as the envelope address.
 */
private fun fieldParts(field: String): List<String> = split(withoutComments(field), setOf(';'))

/** The whitespace-separated words of a part, each quoted string kept inside its word. */
private fun words(part: String): List<String> = split(part, setOf(' ', '\t', '\r', '\n'))

/** [text] without its comments (RFC 5322 section 3.2.2), which may nest; quoted strings stay whole. */
private fun withoutComments(text: String): String =
    buildString {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            when {
                escaped -> escaped = false
                char == '\\' && (quoted || depth > 0) -> escaped = true
                depth == 0 && char == '"' -> quoted = !quoted
                quoted -> Unit
                char == '(' -> depth++
                char == ')' && depth > 0 -> depth--
            }
            if (depth == 0 && char != ')') append(char)
        }
    }

/** [text] split at [separators] outside quoted strings, each piece trimmed, empty ones left out. */
private fun split(
    text: String,
    separators: Set<Char>,
): List<String> {
    val pieces = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var escaped = false
    for (char in text) {
        when {
            escaped -> {
                escaped = false
            }

            char == '\\' && quoted -> {
                escaped = true
            }

            char == '"' -> {
                quoted = !quoted
            }

            !quoted && char in separators -> {
                pieces += current.toString()
                current.clear()
                continue
            }
        }
        current.append(char)
    }
    pieces += current.toString()
    return pieces.map { it.trim() }.filter { it.isNotEmpty() }
}
