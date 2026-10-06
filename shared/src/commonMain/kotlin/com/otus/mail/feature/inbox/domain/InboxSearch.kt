package com.otus.mail.feature.inbox.domain

data class InboxSearch(
    val query: String = "",
    val unreadOnly: Boolean = false,
    val attachmentsOnly: Boolean = false,
    val senders: Set<String> = emptySet(),
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val mailboxes: Set<MailboxScope> = setOf(MailboxScope(null, "Inbox")),
    val accounts: Set<String> = emptySet(),
) {
    val isFiltered: Boolean
        get() = this != InboxSearch()
}

/** All whitespace-separated terms must match; terms can match different message fields. */
fun searchMessages(
    messages: List<MailMessage>,
    search: InboxSearch,
): List<MailMessage> {
    val terms =
        search.query
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
    return messages
        .filter { message ->
            matchesFilters(message, search) &&
                terms.all { term ->
                    listOf(message.subject, message.body, message.sender.name, message.sender.address)
                        .any { it.contains(term, ignoreCase = true) }
                }
        }.sortedWith(compareByDescending<MailMessage> { it.received.date }.thenByDescending { it.received.time })
}

private fun matchesFilters(
    message: MailMessage,
    search: InboxSearch,
): Boolean =
    (!search.unreadOnly || message.attributes.unread) &&
        (!search.attachmentsOnly || message.attributes.attachments.isNotEmpty()) &&
        (search.senders.isEmpty() || message.sender.address in search.senders) &&
        (search.dateStart == null || message.received.date >= search.dateStart) &&
        (search.dateEnd == null || message.received.date <= search.dateEnd) &&
        (search.mailboxes.isEmpty() || search.mailboxes.any { it.matches(message.location) }) &&
        (search.accounts.isEmpty() || message.location.accountId in search.accounts)

/** Senders for the filtered addresses; addresses no longer in [messages] keep a bare address. */
fun selectedSenders(
    messages: List<MailMessage>,
    addresses: Set<String>,
): List<MailSender> {
    if (addresses.isEmpty()) return emptyList()
    val known = messages.map { it.sender }.filter { it.address in addresses }.associateBy { it.address }
    return addresses.map { known[it] ?: MailSender("", it) }
}

/** A null account scopes the mailbox name across accounts, as used by the default Inbox. */
data class MailboxScope(
    val account: String?,
    val name: String,
) {
    fun matches(location: MailLocation): Boolean =
        location.mailbox == name && (account == null || location.accountId == account)
}
