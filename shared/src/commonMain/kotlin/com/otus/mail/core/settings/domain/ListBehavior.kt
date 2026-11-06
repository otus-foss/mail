package com.otus.mail.core.settings.domain

/**
 * How the mail list reads: the inbox row's [pills], in order, what the [inbox] holds, how the
 * filter panel is arranged, the modules the inbox's control panel shows when pulled down, and how
 * the list's rows read.
 */
data class ListBehavior(
    val pills: List<InboxPill> = InboxPill.defaults(readLater = false),
    val inbox: InboxContents = InboxContents(),
    val filters: FilterLayout = FilterLayout(),
    val quickView: List<QuickViewModule> = QuickViewModule.Defaults,
    val layout: MailListLayout = MailListLayout(),
)
