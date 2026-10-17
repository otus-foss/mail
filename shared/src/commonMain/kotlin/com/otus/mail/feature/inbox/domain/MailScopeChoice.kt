package com.otus.mail.feature.inbox.domain

/**
 * Whose mail the pages show, as chosen in "Show mail from": every account, one account, or one
 * address of one account. The choice filters the messages a page has loaded; it loads nothing.
 */
sealed interface MailScopeChoice {
    /** The account whose counts the sidebar shows; null for every account. */
    val accountId: String?

    data object All : MailScopeChoice {
        override val accountId: String? = null
    }

    data class Account(
        override val accountId: String,
    ) : MailScopeChoice

    /** Mail of [accountId] to or from [address], so its Sent and Drafts show too. */
    data class Address(
        override val accountId: String,
        val address: String,
    ) : MailScopeChoice

    /** Whether [message] belongs to this scope. Addresses match without regard to case. */
    fun shows(message: MailMessage): Boolean =
        when (this) {
            All -> true
            is Account -> message.location.accountId == accountId
            is Address -> message.location.accountId == accountId && message.involves(address)
        }

    /** This choice while its account is among [accountIds]; every account once it is gone. */
    fun within(accountIds: Collection<String>): MailScopeChoice =
        if (accountId == null || accountId in accountIds) this else All
}

/** The messages [choice] shows, in their order. */
fun List<MailMessage>.within(choice: MailScopeChoice): List<MailMessage> =
    if (choice == MailScopeChoice.All) this else filter(choice::shows)

/** Whether [address] sent the message or is among its To or Cc recipients. */
fun MailMessage.involves(address: String): Boolean =
    (listOf(sender) + to + cc).any { it.address.equals(address, ignoreCase = true) }
