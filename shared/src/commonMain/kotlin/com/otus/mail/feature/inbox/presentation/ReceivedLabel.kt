package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.components.monthAndDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * When a message arrived, as the mail list's [layout] shows it. A relative style names recent
 * days with [recentDayLabel]; a full style always names the date and adds the time today. Dates
 * follow [DateStyle.monthNames] and [MailListLayout.showThisYear].
 */
fun DateFormatter.listDateLabel(
    received: ReceivedAt,
    today: LocalDate,
    layout: MailListLayout,
): String {
    val date = LocalDate.parse(received.date)
    val day = dayLabel(date, today, layout)
    return when {
        layout.dateStyle.relative -> recentDayLabel(date, received.time, today) ?: day
        date == today -> "$day ${received.time}"
        else -> day
    }
}

/** What [layout]'s date style means, with a date from three weeks ago: "Yesterday, then Oct 8". */
fun DateFormatter.dateStyleExample(
    layout: MailListLayout,
    today: LocalDate,
): String {
    val sample = dayLabel(today.minus(DatePeriod(days = SAMPLE_DAYS_AGO)), today, layout)
    return if (layout.dateStyle.relative) "Yesterday, then $sample" else "Always the date, like $sample"
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

/** The date alone, such as 08.10.2026, 08.10, Oct 8, 2026, or Oct 8. */
private fun DateFormatter.dayLabel(
    date: LocalDate,
    today: LocalDate,
    layout: MailListLayout,
): String {
    val withYear = layout.showThisYear || date.year != today.year
    return when {
        layout.dateStyle.monthNames -> if (withYear) "${date.monthAndDay}, ${date.year}" else date.monthAndDay
        withYear -> format(date)
        else -> formatDayMonth(date)
    }
}

private const val WEEK = 7
private const val SAMPLE_DAYS_AGO = 21
private const val SHORT_DAY = 3

/** The weekday's three-letter name, such as "Mon". */
private val DayOfWeek.shortName: String
    get() = name.take(SHORT_DAY).lowercase().replaceFirstChar { it.uppercase() }
