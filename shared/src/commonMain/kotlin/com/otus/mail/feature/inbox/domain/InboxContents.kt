package com.otus.mail.feature.inbox.domain

/**
 * What the Inbox page lists: mail of every address, or only of [addresses], and the inbox alone
 * or also Spam and Trash. Mail stays in its own folders; this only chooses what the page shows.
 */
data class InboxContents(
    val addresses: List<MailScopeChoice.Address> = emptyList(),
    val spam: Boolean = false,
    val trash: Boolean = false,
) {
    /** Whether the page shows every address's mail. */
    val everyAddress: Boolean
        get() = addresses.isEmpty()

    /** The folder roles the page loads (RFC 8621, section 2), the inbox first. */
    val roles: List<String>
        get() =
            buildList {
                add(INBOX_ROLE)
                if (spam) add(JUNK_ROLE)
                if (trash) add(TRASH_ROLE)
            }

    /** Whether the page lists [message]: any message of every address, else one to or from a chosen address. */
    fun shows(message: MailMessage): Boolean = everyAddress || addresses.any { it.shows(message) }

    /** Whether [address] is chosen; every address counts as chosen while no address is. */
    fun has(address: MailScopeChoice.Address): Boolean = everyAddress || addresses.any { it.matches(address) }

    /**
     * Chooses [address] or leaves it out. Leaving out one of every address keeps the others of
     * [all]; leaving out the last chosen one shows every address again.
     */
    fun with(
        address: MailScopeChoice.Address,
        on: Boolean,
        all: List<MailScopeChoice.Address>,
    ): InboxContents {
        val chosen = if (everyAddress) all else addresses
        val next =
            (if (on) chosen + address else chosen.filterNot { it.matches(address) })
                .distinctBy { it.accountId to it.address.lowercase() }
        return copy(addresses = next.takeIf { it.size < all.size }.orEmpty())
    }

    /** These contents without addresses of accounts other than [accountIds], such as a removed one. */
    fun within(accountIds: Collection<String>): InboxContents =
        copy(addresses = addresses.filter { it.accountId in accountIds })

    private fun MailScopeChoice.Address.matches(other: MailScopeChoice.Address): Boolean =
        accountId == other.accountId && address.equals(other.address, ignoreCase = true)

    companion object {
        /** One `name=value` line per setting, one `address=` line per chosen address; unknown lines are ignored. */
        fun encode(contents: InboxContents): String =
            buildList {
                add("$SPAM=${contents.spam}")
                add("$TRASH=${contents.trash}")
                contents.addresses.forEach { add("$ADDRESS=${it.accountId}\t${it.address}") }
            }.joinToString("\n")

        /** Missing or unreadable values read as the defaults. */
        fun decode(saved: String): InboxContents {
            val lines = saved.lines().map { it.substringBefore("=") to it.substringAfter("=", "") }

            fun flag(name: String) = lines.lastOrNull { it.first == name }?.second?.toBooleanStrictOrNull() ?: false
            return InboxContents(
                addresses =
                    lines
                        .filter { it.first == ADDRESS && '\t' in it.second }
                        .map { (_, value) ->
                            MailScopeChoice.Address(value.substringBefore('\t'), value.substringAfter('\t'))
                        }.filter { it.accountId.isNotBlank() && it.address.isNotBlank() },
                spam = flag(SPAM),
                trash = flag(TRASH),
            )
        }

        private const val SPAM = "spam"
        private const val TRASH = "trash"
        private const val ADDRESS = "address"
        private const val INBOX_ROLE = "inbox"
        private const val JUNK_ROLE = "junk"
        private const val TRASH_ROLE = "trash"
    }
}
