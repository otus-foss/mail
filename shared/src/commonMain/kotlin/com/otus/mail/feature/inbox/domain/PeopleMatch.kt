package com.otus.mail.feature.inbox.domain

/**
 * The people [query] could mean: the name, a word of it, the address, or a part of the address
 * before the @ starts with it, case aside. JMAP servers match search text by whole words, so a
 * half-typed name is matched on the device instead.
 */
fun List<MailSender>.matching(query: String): List<MailSender> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return filter { person -> person.searchWords().any { it.startsWith(needle) } }
}

private fun MailSender.searchWords(): List<String> {
    val name = name.lowercase()
    val address = address.lowercase()
    return listOf(name, address) +
        name.split(' ').filter(String::isNotBlank) +
        address.substringBefore('@').split('.', '_', '-', '+')
}
