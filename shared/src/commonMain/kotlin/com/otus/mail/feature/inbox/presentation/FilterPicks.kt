package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.SenderKey

/** Whether [person]'s mail is kept: one of their addresses is among the chosen senders. */
fun InboxFilters.hasSender(person: FilterPerson): Boolean = from.any { it.overlaps(person.key) }

/** These filters with [person] picked or not; picking replaces a chosen sender that shares an address. */
fun InboxFilters.withSender(
    person: FilterPerson,
    on: Boolean,
): InboxFilters {
    val others = from.filterNot { it.overlaps(person.key) }.toSet()
    return copy(from = if (on) others + person.key else others)
}

/** Whether mail to [person] is kept: their own address in Sent to, or any of their addresses in To. */
fun InboxFilters.hasRecipient(person: FilterPerson): Boolean =
    person.own?.let { it in sentTo }
        ?: person.addresses.any { address -> to.any { it.equals(address, ignoreCase = true) } }

/** These filters with mail to [person] kept or not. */
fun InboxFilters.withRecipient(
    person: FilterPerson,
    on: Boolean,
): InboxFilters {
    val own = person.own
    if (own != null) return copy(sentTo = if (on) sentTo + own else sentTo - own)
    val others = to.filterNot { address -> person.addresses.any { it.equals(address, ignoreCase = true) } }.toSet()
    return copy(to = if (on) others + person.addresses.first() else others)
}

/** The listed person a chosen sender stands for, to show their avatar; null when none is listed. */
fun List<FilterPerson>.personOf(key: SenderKey): FilterPerson? = firstOrNull { it.key.overlaps(key) }

private fun SenderKey.overlaps(other: SenderKey): Boolean =
    addresses.any { address -> other.addresses.any { it.equals(address, ignoreCase = true) } }
