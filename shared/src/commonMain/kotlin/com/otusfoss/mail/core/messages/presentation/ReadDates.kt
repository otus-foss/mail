package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.formatNamedDay
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlinx.datetime.LocalDate

/**
 * When a message arrived, as the Read view's date pill says it: the time today, "Yesterday", the
 * weekday within the last week, then the day and month, with the year when it is not this year.
 */
fun DateFormatter.shortLabel(
    received: ReceivedAt,
    today: LocalDate,
): String {
    val date = LocalDate.parse(received.date)
    return recentDayLabel(date, received.time, today) ?: formatNamedDay(date, withYear = date.year != today.year)
}

/** The full timestamp the date pill opens, such as "Oct 12, 2026, 09:14". */
fun DateFormatter.fullLabel(received: ReceivedAt): String =
    "${formatNamedDay(LocalDate.parse(received.date), withYear = true)}, ${received.time}"

/** Where a page sits in its conversation, newest first: "1/6 · 12 Oct" for [index] 0 of [count]. */
fun positionDetail(
    index: Int,
    count: Int,
    date: String,
): String = "${index + 1}/$count · $date"
