package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.within
import com.otus.mail.feature.inbox.presentation.InboxContext

/**
 * What the quick view's modules read: the page's filters and scope through [overlays] and [page],
 * its loaded [messages], and [onOpenMessage] for a file's mail. Each module closes the quick view
 * when it applies its choice.
 */
internal class QuickSources(
    val overlays: InboxOverlays,
    val page: InboxContext,
    messages: List<MailMessage>,
    val onOpenMessage: (MailMessage) -> Unit,
) {
    val scoped: List<MailMessage> = messages.within(page.scope?.chosen ?: MailScopeChoice.All)

    /** Closes the quick view and shows only mail that meets [filters]. */
    fun show(filters: InboxFilters) {
        overlays.quickOpen = false
        overlays.filters.update(filters)
    }

    fun close() {
        overlays.quickOpen = false
    }
}
