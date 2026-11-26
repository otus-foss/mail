package com.otusfoss.mail.feature.inbox.domain

import com.otusfoss.mail.core.messages.domain.ListFilter
import com.otusfoss.mail.core.messages.domain.ReadStatus

/** Whether a bar button's [filter] is on. */
fun InboxFilters.has(filter: ListFilter): Boolean =
    when (filter) {
        ListFilter.Unread -> status == ReadStatus.Unread
        is ListFilter.Quick -> has(filter.facet)
        is ListFilter.Type -> has(filter.facet)
    }

/** These filters with a bar button's [filter] on or off; the others stay as they are. */
fun InboxFilters.with(
    filter: ListFilter,
    on: Boolean,
): InboxFilters =
    when (filter) {
        ListFilter.Unread -> copy(status = if (on) ReadStatus.Unread else ReadStatus.All)
        is ListFilter.Quick -> with(filter.facet, on)
        is ListFilter.Type -> with(filter.facet, on)
    }
