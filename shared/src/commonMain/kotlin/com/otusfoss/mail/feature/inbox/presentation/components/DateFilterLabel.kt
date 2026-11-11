package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.currentLocalDate
import com.otusfoss.core.dateformat.formatRelative
import com.otusfoss.core.dateformat.formatRelativeRange
import com.otusfoss.mail.feature.inbox.domain.InboxSearch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Presets keep short labels; custom dates follow the user's date format. */
internal fun dateFilterLabel(
    search: InboxSearch,
    dateFormatter: DateFormatter,
    today: LocalDate = currentLocalDate(),
): String {
    val start = search.dateStart ?: return InboxFilter.Date.title
    val first = LocalDate.parse(start)
    val last = LocalDate.parse(search.dateEnd ?: start)
    return when {
        first == today.minus(WEEK_RANGE_OFFSET, DateTimeUnit.DAY) && last == today -> "This week"
        first == today.minus(MONTH_RANGE_OFFSET, DateTimeUnit.DAY) && last == today -> "This month"
        first == last -> dateFormatter.formatRelative(first, today)
        else -> dateFormatter.formatRelativeRange(first, last, today, separator = "–")
    }
}

private const val WEEK_RANGE_OFFSET = 6
private const val MONTH_RANGE_OFFSET = 29
