package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.category.InboxCategories
import com.otus.mail.feature.inbox.domain.category.MailCategory

/**
 * The Inbox's category pills: the saved [settings], the [selected] pill, each pill's [unread]
 * count, and [onSelect], which shows another pill's mail. The app keeps the selection.
 */
class CategoryTabs(
    val settings: InboxCategories,
    val selected: MailCategory,
    val unread: Map<MailCategory, Int>,
    val onSelect: (MailCategory) -> Unit,
) {
    /** The [selected] pill's share of [messages]. */
    fun shown(messages: List<MailMessage>): List<MailMessage> = settings.inTab(messages, selected)

    /** Pressing the selected pill goes back to Primary, as a second press of a folder pill does. */
    fun press(category: MailCategory) {
        onSelect(if (category == selected) MailCategory.Primary else category)
    }
}
