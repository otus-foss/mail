package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.ResolvedAvatar
import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.SenderKey
import com.otus.mail.feature.inbox.domain.lastReceived

/**
 * One person the filter panel's From or To line matches: a person with every address they use, the most
 * used first, their resolved [avatar] when loaded mail has one, and in how many [conversations]
 * of the view or of search they appear. [own] marks one of the user's addresses in the To line,
 * which the login named [accountName] receives; its [avatar] is that account's.
 */
data class FilterPerson(
    val name: String,
    val addresses: List<String>,
    val avatar: ResolvedAvatar? = null,
    val conversations: Int = 0,
    val own: OwnAddress? = null,
    val accountName: String = "",
) {
    /** The From filter that keeps this person's mail. */
    val key: SenderKey
        get() = SenderKey(name, addresses.map(String::lowercase).toSet())

    /** The name, or the first address while the name is blank. */
    val label: String
        get() = name.ifBlank { addresses.first() }
}

/**
 * The From line's matches: the senders of the loaded [messages] and the [found] people, filtered by
 * [query]. Senders sharing a display name and a domain merge into one person. People who appear
 * in more conversations come first, and of those the one seen more recently.
 */
fun fromPeople(
    messages: List<MailMessage>,
    found: List<FoundPerson> = emptyList(),
    query: String = "",
    limit: Int = MAX_PEOPLE,
): List<FilterPerson> {
    val loaded = messages.map { FoundPerson(it.sender, 1, it.lastSeen) }
    return ranked(loaded, found, query, avatars(messages.map { it.sender }), limit)
}

/**
 * The To line's matches: the user's [own] addresses first, with their account's avatar and name from
 * [accountNames] and how many loaded conversations reached them, then the other recipients of
 * [messages] and the [found] people, ranked as [fromPeople] ranks senders.
 */
fun toPeople(
    messages: List<MailMessage>,
    own: List<OwnAddress>,
    accountNames: Map<String, String> = emptyMap(),
    found: List<FoundPerson> = emptyList(),
    query: String = "",
    limit: Int = MAX_PEOPLE,
): List<FilterPerson> {
    val mine = own.map { it.address.lowercase() }.toSet()
    val received = messages.map { message -> message to (message.to + message.cc) }
    val accountAvatars = messages.associate { it.location.accountId to it.location.accountAvatar }
    val ownPeople =
        own
            .filter { it.address.matches(query) || accountNames[it.accountId].orEmpty().matches(query) }
            .map { address ->
                val account = accountNames[address.accountId].orEmpty()
                FilterPerson(
                    name = account,
                    addresses = listOf(address.address),
                    avatar = accountAvatars[address.accountId],
                    conversations = received.count { (_, people) -> people.any { it.isAt(address.address) } },
                    own = address,
                    accountName = account,
                )
            }
    val loaded = received.flatMap { (message, people) -> people.map { FoundPerson(it, 1, message.lastSeen) } }
    val others =
        ranked(
            loaded.filter { it.person.address.lowercase() !in mine },
            found.filter { it.person.address.lowercase() !in mine },
            query,
            avatars(received.flatMap { (_, people) -> people }),
            limit,
        )
    return ownPeople + others
}

/** One address as loaded rows and searches saw it; rows add up, a search counts on its own. */
private class Seen(
    val sender: MailSender,
    val rows: Int,
    val searched: Int,
    val lastSeen: String,
) {
    val conversations: Int
        get() = maxOf(rows, searched)

    /** Senders with the same name at the same domain are one person; nameless ones stay apart. */
    val mergeKey: String
        get() {
            val name = sender.name.trim().lowercase()
            val address = sender.address.lowercase()
            return if (name.isEmpty()) address else "$name@${address.substringAfter('@')}"
        }
}

private fun ranked(
    loaded: List<FoundPerson>,
    found: List<FoundPerson>,
    query: String,
    avatars: Map<String, ResolvedAvatar>,
    limit: Int,
): List<FilterPerson> {
    val wanted = { found: FoundPerson ->
        val person = found.person
        person.address.isNotBlank() && (person.name.matches(query) || person.address.matches(query))
    }
    val rows = loaded.filter(wanted).groupBy { it.person.address.lowercase() }
    val searched = found.filter(wanted).groupBy { it.person.address.lowercase() }
    return (rows.keys + searched.keys)
        .map { address ->
            val seen = rows[address].orEmpty() + searched[address].orEmpty()
            val name = seen.firstNotNullOfOrNull { it.person.name.ifBlank { null } }.orEmpty()
            Seen(
                MailSender(name, seen.first().person.address),
                rows = rows[address].orEmpty().size,
                searched = searched[address].orEmpty().maxOfOrNull { it.conversations } ?: 0,
                lastSeen = seen.maxOf { it.lastReceived },
            )
        }.groupBy { it.mergeKey }
        .values
        .map { group -> group.sortedByDescending { it.conversations } }
        .sortedWith(
            compareByDescending<List<Seen>> { group -> group.sumOf { it.conversations } }
                .thenByDescending { group -> group.maxOf { it.lastSeen } },
        ).take(limit)
        .map { group ->
            FilterPerson(
                name = group.first().sender.name,
                addresses = group.map { it.sender.address },
                avatar = group.firstNotNullOfOrNull { avatars[it.sender.address.lowercase()] },
                conversations = group.sumOf { it.conversations },
            )
        }
}

private fun avatars(people: List<MailSender>): Map<String, ResolvedAvatar> =
    people.mapNotNull { sender -> sender.avatar?.let { sender.address.lowercase() to it } }.toMap()

private fun MailSender.isAt(address: String): Boolean = this.address.equals(address, ignoreCase = true)

private fun String.matches(query: String): Boolean = query.isBlank() || contains(query.trim(), ignoreCase = true)

/** When the row's conversation last had mail, as a sortable ISO date and time. */
private val MailMessage.lastSeen: String
    get() = lastReceived.let { "${it.date}T${it.time}" }

private const val MAX_PEOPLE = 30
