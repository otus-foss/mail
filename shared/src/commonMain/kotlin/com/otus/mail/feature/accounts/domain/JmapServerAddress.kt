package com.otus.mail.feature.accounts.domain

/** What the user typed as their server, resolved to a JMAP session URL when possible. */
sealed interface JmapServerAddress {
    data object Empty : JmapServerAddress

    /** JMAP requires TLS; plain HTTP is refused rather than silently upgraded. */
    data object Insecure : JmapServerAddress

    data object Invalid : JmapServerAddress

    data class Valid(
        val sessionUrl: String,
    ) : JmapServerAddress
}

private val hostPattern =
    Regex("^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:[0-9]{1,5})?$")

/**
 * A bare host such as `mail.example.com` uses the standard discovery path, `/.well-known/jmap`
 * (RFC 8620, section 2.2). A full `https://` URL with a path is used as the session URL itself.
 */
fun parseJmapServer(input: String): JmapServerAddress {
    val text = input.trim()
    val scheme = text.substringBefore("://", missingDelimiterValue = "").lowercase()
    val rest = if (scheme.isEmpty()) text else text.substringAfter("://")
    val authority = rest.substringBefore('/')
    val path = rest.removePrefix(authority).trimEnd('/')
    return when {
        text.isEmpty() -> JmapServerAddress.Empty
        scheme == "http" -> JmapServerAddress.Insecure
        scheme !in setOf("", "https") || !hostPattern.matches(authority) -> JmapServerAddress.Invalid
        else -> JmapServerAddress.Valid("https://${authority.lowercase()}${path.ifEmpty { "/.well-known/jmap" }}")
    }
}

/** Details for connecting one account. The password is passed on, never stored by this model. */
data class JmapAccountDraft(
    val sessionUrl: String,
    val username: String,
    val password: String,
    /** Other origins the user approved to receive these credentials. */
    val trustedOrigins: Set<String> = emptySet(),
) {
    override fun toString(): String =
        "JmapAccountDraft(sessionUrl=$sessionUrl, username=$username, password=***, trustedOrigins=$trustedOrigins)"
}

/** HTTP Basic usernames cannot contain a colon (RFC 7617, section 2). */
fun isValidUsername(username: String): Boolean = username.isNotBlank() && ':' !in username
