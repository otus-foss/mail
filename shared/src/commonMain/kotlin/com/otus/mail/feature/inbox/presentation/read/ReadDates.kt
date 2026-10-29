package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.components.dayAndMonth
import com.otus.mail.feature.inbox.presentation.recentDayLabel
import kotlinx.datetime.LocalDate

/**
 * When a message arrived, as the Read view's date pill says it: the time today, "Yesterday", the
 * weekday within the last week, then the day and month, with the year when it is not this year.
 */
fun ReceivedAt.shortLabel(today: LocalDate): String {
    val date = LocalDate.parse(date)
    return recentDayLabel(date, time, today)
        ?: if (date.year == today.year) date.dayAndMonth else "${date.dayAndMonth} ${date.year}"
}

/** The full timestamp the date pill opens, such as "12 Oct 2026, 09:14". */
fun ReceivedAt.fullLabel(): String {
    val date = LocalDate.parse(date)
    return "${date.dayAndMonth} ${date.year}, $time"
}

/** Where a page sits in its conversation, newest first: "1/6 · 12 Oct" for [index] 0 of [count]. */
fun positionDetail(
    index: Int,
    count: Int,
    date: String,
): String = "${index + 1}/$count · $date"
