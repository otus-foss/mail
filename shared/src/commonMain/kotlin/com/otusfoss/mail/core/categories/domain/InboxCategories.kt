package com.otusfoss.mail.core.categories.domain

/**
 * The Inbox's categories as the user set them up: whether the Inbox sorts its mail at all, the
 * categories it [hidden]s, whose mail Primary shows instead, and the user's choice for some
 * [senders], by lowercase address, which comes before anything the classifier finds. Primary is
 * always shown.
 */
data class InboxCategories(
    val enabled: Boolean = false,
    val hidden: Set<MailCategory> = emptySet(),
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
     * Where mail from [sender] that the classifier gave [verdict] shows: the user's choice for the
     * sender, else the verdict, else Primary. A hidden category's mail shows in Primary, and so does
     * time-sensitive mail.
     */
    fun placement(
        sender: String,
        verdict: CategoryVerdict?,
    ): CategoryPlacement {
        val chosen = senderChoice(sender)
        val found =
            if (chosen != null) {
                CategoryPlacement(chosen, listOf(CategoryReason(ReasonKind.SenderChoice, sender)))
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
