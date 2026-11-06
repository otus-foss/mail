package com.otus.mail.feature.compose.presentation.components

import com.otus.dateformat.DateFormatter
import com.otus.dateformat.clockTime
import com.otus.dateformat.formatNamedDay
import com.otus.dateformat.formatRelative
import com.otus.dateformat.shortWeekday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * When a scheduled message goes, as the Send later sheet shows it: "Today, 18:00", tomorrow's
 * weekday such as "Sat, 8:00", or a date such as "Mon Oct 19, 8:00".
 */
internal fun DateFormatter.sendLaterLabel(
    at: LocalDateTime,
    now: LocalDateTime,
): String {
    val day =
        when (at.date) {
            now.date -> formatRelative(at.date, today = now.date)
            now.date.plus(DatePeriod(days = 1)) -> shortWeekday(at.dayOfWeek)
            else -> datedDay(at)
        }
    return "$day, ${clockTime(at.time)}"
}

/** When a scheduled message goes, with its date, as the notice and toast say: "Scheduled for Mon Oct 19, 8:00". */
internal fun DateFormatter.scheduledLabel(at: LocalDateTime): String =
    "Scheduled for ${datedDay(at)}, ${clockTime(at.time)}"

private fun DateFormatter.datedDay(at: LocalDateTime): String =
    "${shortWeekday(at.dayOfWeek)} ${formatNamedDay(at.date)}"
