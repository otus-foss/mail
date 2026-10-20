package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * When a message arrived, as its row shows it: the time today, "Yesterday", the weekday within
 * the last week, and the saved date format before that.
 */
fun DateFormatter.receivedLabel(
    received: ReceivedAt,
    today: LocalDate,
): String {
    val date = LocalDate.parse(received.date)
    return when {
        date == today -> received.time
        date == today.minus(DatePeriod(days = 1)) -> "Yesterday"
        date > today.minus(DatePeriod(days = WEEK)) && date < today -> date.dayOfWeek.shortName
        else -> format(date)
    }
}

/** When a message arrived, in the mail list's [style]: [receivedLabel], or the date with the time for today. */
fun DateFormatter.listDateLabel(
    received: ReceivedAt,
    today: LocalDate,
    style: DateStyle,
): String =
    when (style) {
        DateStyle.Relative -> {
            receivedLabel(received, today)
        }

        DateStyle.Full -> {
            val date = LocalDate.parse(received.date)
            if (date == today) "${format(date)} ${received.time}" else format(date)
        }
    }

private const val WEEK = 7
private const val SHORT_DAY = 3

/** The weekday's three-letter name, such as "Mon". */
internal val DayOfWeek.shortName: String
    get() = name.take(SHORT_DAY).lowercase().replaceFirstChar { it.uppercase() }
