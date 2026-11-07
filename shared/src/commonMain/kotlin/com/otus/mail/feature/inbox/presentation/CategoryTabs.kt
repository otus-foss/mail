package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.categories.domain.CategoryPlacement
import com.otus.mail.core.categories.domain.InboxCategories
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.categories.presentation.label
import com.otus.mail.core.messages.domain.MailMessage

/**
 * The Inbox's category pills: the saved [settings], the [selected] pill, each pill's [unread]
 * count, and [onSelect], which shows another pill's mail.
 */
class CategoryTabs(
    val settings: InboxCategories,
    val selected: MailCategory,
    val unread: Map<MailCategory, Int>,
    val onSelect: (MailCategory) -> Unit,
) {
    /** The [selected] pill's share of [messages], in their order. */
    fun shown(messages: List<MailMessage>): List<MailMessage> =
        messages.filter { settings.placement(it).shownIn(selected) }

    /** Pressing the selected pill goes back to Primary, as a second press of a folder pill does. */
    fun press(category: MailCategory) {
        onSelect(if (category == selected) MailCategory.Primary else category)
    }

    /** What an empty pill says; null for Primary, which says what the page does. */
    val emptyTitle: String?
        get() = selected.takeIf { it != MailCategory.Primary }?.let { "Nothing in ${it.label}" }
}

/** Where the Inbox shows [message] and why. */
fun InboxCategories.placement(message: MailMessage): CategoryPlacement =
    placement(message.sender.address, message.category)

/** How many unread [messages] each shown pill holds. */
fun InboxCategories.unreadCounts(messages: List<MailMessage>): Map<MailCategory, Int> {
    val unread = messages.filter { it.attributes.unread }.map(::placement)
    return tabs.associateWith { tab -> unread.count { it.shownIn(tab) } }
}
