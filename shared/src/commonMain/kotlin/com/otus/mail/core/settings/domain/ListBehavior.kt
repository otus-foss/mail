package com.otus.mail.core.settings.domain

import com.otus.mail.core.categories.domain.InboxCategories

/**
 * How the mail list reads: the inbox row's [pills], in order, what the [inbox] holds, how the
 * filter panel is arranged, the modules the inbox's control panel shows when pulled down, how
 * the list's rows read, and the Inbox's [categories]; see docs/categories.md.
 */
data class ListBehavior(
    val pills: List<InboxPill> = InboxPill.defaults(readLater = false),
    val inbox: InboxContents = InboxContents(),
    val filters: FilterLayout = FilterLayout(),
    val quickView: List<QuickViewModule> = QuickViewModule.Defaults,
    val layout: MailListLayout = MailListLayout(),
    val categories: InboxCategories = InboxCategories(),
)
