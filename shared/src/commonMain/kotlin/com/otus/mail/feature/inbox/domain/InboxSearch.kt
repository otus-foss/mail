package com.otus.mail.feature.inbox.domain

data class InboxSearch(
    val query: String = "",
    val senders: Set<String> = emptySet(),
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val mailboxes: Set<MailboxScope> = setOf(MailboxScope(null, INBOX_MAILBOX)),
    val accounts: Set<String> = emptySet(),
    /** Where the terms must match. */
    val field: SearchField = SearchField.Anywhere,
    /** Only mail with attachments. */
    val withFiles: Boolean = false,
)

/** The part of a message a search looks in. */
enum class SearchField { Anywhere, Subject, Body }

/** Looks only in [only], or anywhere again when it already does. */
fun InboxSearch.toggled(only: SearchField): InboxSearch =
    copy(field = if (field == only) SearchField.Anywhere else only)

/**
 * All whitespace-separated terms must match; terms can match different message fields. The
 * [filters] the loaded data can tell, such as Unread or Flagged, apply here too, so mail changed
 * since it loaded and mail from sources without a server follow them. [filters] also set the order.
 */
fun searchMessages(
    messages: List<MailMessage>,
    search: InboxSearch,
    filters: InboxFilters = InboxFilters(),
): List<MailMessage> {
    val terms =
        search.query
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
    val newestFirst =
        compareByDescending<MailMessage> { it.lastReceived.date }.thenByDescending { it.lastReceived.time }
    return messages
        .filter { message ->
            matchesFilters(message, search) &&
                message.passes(filters) &&
                terms.all { term ->
                    message.searchedText(search.field).any { it.contains(term, ignoreCase = true) }
                }
        }.sortedWith(if (filters.sort == FilterSort.NewestFirst) newestFirst else newestFirst.reversed())
}

private fun MailMessage.searchedText(field: SearchField): List<String> =
    when (field) {
        SearchField.Anywhere -> listOf(subject, body, sender.name, sender.address)
        SearchField.Subject -> listOf(subject)
        SearchField.Body -> listOf(body)
    }

private fun matchesFilters(
    message: MailMessage,
    search: InboxSearch,
): Boolean =
    (search.senders.isEmpty() || message.sender.address in search.senders) &&
        (!search.withFiles || message.attributes.attachments.isNotEmpty()) &&
        (search.dateStart == null || message.received.date >= search.dateStart) &&
        (search.dateEnd == null || message.received.date <= search.dateEnd) &&
        (search.mailboxes.isEmpty() || search.mailboxes.any { it.matches(message.location) }) &&
        (search.accounts.isEmpty() || message.location.accountId in search.accounts)

/** Whether the message meets [filters] as far as its loaded marks tell; the server checks the rest. */
private fun MailMessage.passes(filters: InboxFilters): Boolean =
    when (filters.status) {
        ReadStatus.All -> true
        ReadStatus.Unread -> attributes.unread
        ReadStatus.Read -> !attributes.unread
    } &&
        (!filters.has(QuickFacet.Flagged) || attributes.flagged) &&
        (!filters.has(QuickFacet.Attachments) || attributes.attachments.isNotEmpty()) &&
        (!filters.has(QuickFacet.Conversations) || conversation?.exchanged == true) &&
        (!filters.has(TypeFacet.CalendarInvites) || attributes.invite)

/**
 * The mailbox and account scope to load; null while both match [default]'s scope.
 * An empty mailbox choice loads every mailbox of the selected accounts.
 */
fun InboxSearch.scopeToLoad(default: InboxSearch): MailScope? =
    when {
        mailboxes == default.mailboxes && accounts == default.accounts -> null
        mailboxes.isEmpty() && accounts.isEmpty() -> MailScope.AllMailboxes
        else -> MailScope.Mailboxes(mailboxes, accounts)
    }

/** Senders for the filtered addresses; addresses no longer in [messages] keep a bare address. */
fun selectedSenders(
    messages: List<MailMessage>,
    addresses: Set<String>,
): List<MailSender> {
    if (addresses.isEmpty()) return emptyList()
    val known = messages.map { it.sender }.filter { it.address in addresses }.associateBy { it.address }
    return addresses.map { known[it] ?: MailSender("", it) }
}

/** Accounts for the filtered ids, including saved accounts with no loaded messages. */
fun selectedAccounts(
    messages: List<MailMessage>,
    accountIds: Set<String>,
    accounts: List<MailLocation>? = null,
): List<MailLocation> {
    val loaded = messages.map { it.location }.associateBy { it.accountId }
    val known = (accounts ?: loaded.values.toList()).associateBy { it.accountId }
    return accountIds.map { id ->
        known[id]?.let { it.copy(accountAvatar = loaded[id]?.accountAvatar ?: it.accountAvatar) }
            ?: MailLocation("", id, id)
    }
}

/** A null account scopes the mailbox name across accounts, as used by the default Inbox. */
data class MailboxScope(
    val account: String?,
    val name: String,
) {
    fun matches(location: MailLocation): Boolean =
        location.mailbox == name && (account == null || location.accountId == account)
}

/** Whether [query] is being typed as an email address: one word with an @ in it. */
fun looksLikeAddress(query: String): Boolean = query.trim().let { '@' in it && it.none(Char::isWhitespace) }
