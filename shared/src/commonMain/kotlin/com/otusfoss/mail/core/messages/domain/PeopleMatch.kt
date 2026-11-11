package com.otusfoss.mail.core.messages.domain

/**
 * The people [query] could mean, best first: those whose name starts with it, then a word of their
 * name, then their address, then any part of their address. Text with an @ matches each half on its
 * side of the address, so "flo@pur" finds florian@purbank.example. People who score the same keep their
 * order. JMAP servers match search text by whole words, so half-typed text is matched on the device.
 */
fun List<MailSender>.matching(query: String): List<MailSender> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return map { it to it.score(needle) }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { (_, score) -> score }
        .map { (person) -> person }
}

private fun MailSender.score(needle: String): Int {
    val normalizedName = name.lowercase()
    val normalizedAddress = address.lowercase()
    return when {
        normalizedName.startsWith(needle) -> NAME_START
        normalizedName.split(' ').any { it.startsWith(needle) } -> NAME_WORD
        normalizedAddress.startsWith(needle) -> ADDRESS_START
        normalizedAddress.containsHalves(needle) -> ADDRESS_ANYWHERE
        else -> 0
    }
}

private fun String.containsHalves(needle: String): Boolean =
    if ('@' in needle) {
        needle.substringBefore('@') in substringBefore('@') && needle.substringAfter('@') in substringAfter('@')
    } else {
        needle in this
    }

private const val NAME_START = 4
private const val NAME_WORD = 3
private const val ADDRESS_START = 2
private const val ADDRESS_ANYWHERE = 1
