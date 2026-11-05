package com.otus.mail.feature.inbox.domain.category

import com.otus.mail.feature.inbox.domain.MailMessage

/**
 * The Inbox's categories as the user set them up: whether the Inbox sorts its mail at all, the
 * categories it [hidden]s, whose mail Primary shows instead, and the user's choice for some
 * [senders], by lowercase address, which comes before anything the classifier finds. Primary is
 * always shown.
 */
data class InboxCategories(
    val enabled: Boolean = false,
    val hidden: Set<MailCategory> = DEFAULT_HIDDEN,
    val senders: Map<String, MailCategory> = emptyMap(),
) {
    /** The pills the Inbox shows, Primary first. */
    val tabs: List<MailCategory>
        get() = MailCategory.entries.filter(::shows)

    fun shows(category: MailCategory): Boolean = category == MailCategory.Primary || category !in hidden

    /** These categories with [category] shown or hidden; Primary always stays. */
    fun withShown(
        category: MailCategory,
        shown: Boolean,
    ): InboxCategories =
        when {
            category == MailCategory.Primary -> this
            shown -> copy(hidden = hidden - category)
            else -> copy(hidden = hidden + category)
        }

    /** The user's category for mail from [address]; null while the classifier decides. */
    fun senderChoice(address: String): MailCategory? = senders[address.trim().lowercase()]

    /** Mail from [address] always goes to [category] from now on; null hands it back to the classifier. */
    fun withSender(
        address: String,
        category: MailCategory?,
    ): InboxCategories {
        val key = address.trim().lowercase()
        if (key.isEmpty()) return this
        return copy(senders = if (category == null) senders - key else senders + (key to category))
    }

    /**
     * Where [message] shows: the user's choice for its sender, else the classifier's verdict, else
     * Primary. A hidden category's mail shows in Primary, and so does time-sensitive mail.
     */
    fun placement(message: MailMessage): CategoryPlacement {
        val verdict = message.category
        val chosen = senderChoice(message.sender.address)
        val found =
            if (chosen != null) {
                CategoryPlacement(chosen, listOf(CategoryReason(ReasonKind.SenderChoice, message.sender.address)))
            } else {
                CategoryPlacement(verdict?.category ?: MailCategory.Primary, verdict?.reasons.orEmpty())
            }
        val timeSensitive = verdict?.timeSensitive == true
        return if (shows(found.category)) {
            found.copy(timeSensitive = timeSensitive)
        } else {
            val hiddenReason = CategoryReason(ReasonKind.HiddenCategory, found.category.storageValue)
            CategoryPlacement(MailCategory.Primary, listOf(hiddenReason) + found.reasons, timeSensitive)
        }
    }

    /** The messages the [tab] pill shows, in their order. */
    fun inTab(
        messages: List<MailMessage>,
        tab: MailCategory,
    ): List<MailMessage> = messages.filter { placement(it).shownIn(tab) }

    /** How many unread messages each shown pill holds. */
    fun unreadCounts(messages: List<MailMessage>): Map<MailCategory, Int> {
        val unread = messages.filter { it.attributes.unread }.map(::placement)
        return tabs.associateWith { tab -> unread.count { it.shownIn(tab) } }
    }

    companion object {
        /** A new Inbox shows every category. */
        val DEFAULT_HIDDEN: Set<MailCategory> = emptySet()

        /** [categories]' hidden set as one stored value, one category per line. */
        fun encodeHidden(categories: InboxCategories): String =
            categories.hidden.joinToString("\n") { it.storageValue }

        /** Lines this version does not know are left out; null keeps the default. */
        fun decodeHidden(saved: String?): Set<MailCategory>? =
            saved?.lines()?.mapNotNull(MailCategory::fromStorageValue)?.toSet()

        /** [categories]' sender choices as one stored value: an address, a tab, and a category per line. */
        fun encodeSenders(categories: InboxCategories): String =
            categories.senders.entries.joinToString("\n") { (address, chosen) -> "$address\t${chosen.storageValue}" }

        /** Lines without a known category are left out. */
        fun decodeSenders(saved: String?): Map<String, MailCategory> =
            saved
                ?.lines()
                ?.mapNotNull { line ->
                    val address = line.substringBefore('\t').trim().lowercase()
                    MailCategory.fromStorageValue(line.substringAfter('\t', ""))
                        ?.takeIf { address.isNotEmpty() }
                        ?.let { address to it }
                }?.toMap()
                .orEmpty()
    }
}

/**
 * Where a message shows and why: [reasons] holds the user's choice or the classifier's, with a
 * hidden category's reason first. [timeSensitive] mail shows in Primary as well.
 */
data class CategoryPlacement(
    val category: MailCategory,
    val reasons: List<CategoryReason>,
    val timeSensitive: Boolean = false,
) {
    fun shownIn(tab: MailCategory): Boolean = category == tab || (tab == MailCategory.Primary && timeSensitive)
}
