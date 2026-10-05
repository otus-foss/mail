package com.otus.mail.core.avatar

internal fun isAvatarEmail(email: String): Boolean =
    email.count { it == '@' } == 1 && email.substringBefore('@').isNotBlank() &&
        email.substringAfter('@').isNotBlank() && email.none { it.isWhitespace() || it.isISOControl() }

/** Conservative ASCII hostname handling. IDNs and internal hosts retain their local fallback. */
fun avatarIconDomain(email: String): String? {
    if (!isAvatarEmail(email)) return null
    val domain = email.substringAfter('@').lowercase()
    return domain.takeIf(::isPublicAvatarHost)
}

internal fun isPublicAvatarHost(host: String): Boolean {
    val labels = host.lowercase().split('.')
    return labels.size > 1 &&
        labels.all {
            it.isNotEmpty() && it.length <= MAX_LABEL_LENGTH &&
                it.first().isLetterOrDigit() && it.last().isLetterOrDigit() &&
                it.all { character -> character in 'a'..'z' || character in '0'..'9' || character == '-' }
        } && labels.last().any { it in 'a'..'z' } && labels.last() !in INTERNAL_SUFFIXES
}

private const val MAX_LABEL_LENGTH = 63
private val INTERNAL_SUFFIXES = setOf("local", "localhost", "internal", "test", "invalid")
