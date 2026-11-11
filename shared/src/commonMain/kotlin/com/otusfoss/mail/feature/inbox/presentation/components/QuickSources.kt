package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.within
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState

/**
 * What the quick view's modules read: the page's [filters] and scope through [page], its loaded
 * [messages], and [onOpenMessage] for a file's mail. Each module runs [onDone], which brings the
 * panel back to its usual height, when it applies its choice.
 */
internal class QuickSources(
    val filters: InboxFilterState,
    val page: InboxContext,
    val messages: List<MailMessage>,
    val onOpenMessage: (MailMessage) -> Unit,
    private val onDone: () -> Unit,
) {
    val scoped: List<MailMessage> = messages.within(page.scope?.chosen ?: MailScopeChoice.All)

    /** Brings the panel back and shows only mail that meets [chosen]. */
    fun show(chosen: InboxFilters) {
        onDone()
        filters.update(chosen)
    }

    fun close() = onDone()
}
