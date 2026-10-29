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
    return recentDayLabel(date, received.time, today) ?: format(date)
}

/** The [time] for [today], "Yesterday", or the weekday within the last week; null for other dates. */
internal fun recentDayLabel(
    date: LocalDate,
    time: String,
    today: LocalDate,
): String? =
    when {
        date == today -> time
        date == today.minus(DatePeriod(days = 1)) -> "Yesterday"
        date > today.minus(DatePeriod(days = WEEK)) && date < today -> date.dayOfWeek.shortName
        else -> null
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
private val DayOfWeek.shortName: String
    get() = name.take(SHORT_DAY).lowercase().replaceFirstChar { it.uppercase() }
