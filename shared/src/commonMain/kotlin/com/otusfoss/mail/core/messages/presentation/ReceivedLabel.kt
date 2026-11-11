package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.clockTime
import com.otusfoss.core.dateformat.formatNamedDay
import com.otusfoss.core.dateformat.formatRecentDay
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.settings.domain.DateStyle
import com.otusfoss.mail.core.settings.domain.MailListLayout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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
internal fun DateFormatter.recentDayLabel(
    date: LocalDate,
    time: String,
    today: LocalDate,
): String? = if (date == today) time else formatRecentDay(date, today)

/** The date alone, such as 08.10.2026, 08.10, Oct 8, 2026, or Oct 8, in the order of the date format. */
private fun DateFormatter.dayLabel(
    date: LocalDate,
    today: LocalDate,
    layout: MailListLayout,
): String {
    val withYear = layout.showThisYear || date.year != today.year
    return when {
        layout.dateStyle.monthNames -> formatNamedDay(date, withYear)
        withYear -> format(date)
        else -> formatDayMonth(date)
    }
}

private const val SAMPLE_DAYS_AGO = 21

/** When cached mail last matched the server: the time today, such as "14:32", or else the date, "Oct 30". */
internal fun DateFormatter.syncedLabel(
    at: LocalDateTime,
    now: LocalDateTime,
): String = if (at.date == now.date) clockTime(at.time) else formatNamedDay(at.date)
