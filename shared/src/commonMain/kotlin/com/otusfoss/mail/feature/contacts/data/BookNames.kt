package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jmap.login.JmapLogin

/**
 * A book's name without the account in brackets that some servers append, such as Stalwart's
 * "Address Book (ada@example.org)" or "Address Book (ada.lovelace)" with the JMAP [account]'s
 * name: the row already says which login the book comes from.
 */
internal fun bookName(
    name: String,
    login: JmapLogin,
    account: String,
): String {
    val own = setOf(login.username, login.name, login.username.substringBefore('@'), account).map { it.lowercase() }
    val bracket = Regex("""\s*\(([^()]*)\)\s*$""").find(name)
    val inside =
        bracket
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
    // An account in brackets: the login's or account's own name, or anything shaped like a
    // username or address, such as "ada.lovelace" or "ada@example.org".
    val isAccount = inside.lowercase() in own || (' ' !in inside && ('@' in inside || '.' in inside))
    val trimmed = if (bracket != null && isAccount) name.removeRange(bracket.range) else name
    return trimmed.trim().ifBlank { "Contacts" }
}
