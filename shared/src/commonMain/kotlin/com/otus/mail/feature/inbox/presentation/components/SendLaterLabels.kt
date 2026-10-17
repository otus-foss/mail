package com.otus.mail.feature.inbox.presentation.components

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * When a scheduled message goes, as the Send later sheet shows it: "Today, 18:00", tomorrow's
 * weekday such as "Sat, 8:00", or a date such as "Mon 19 Oct, 8:00".
 */
internal fun LocalDateTime.sendLaterLabel(now: LocalDateTime): String {
    val day =
        when {
            date == now.date -> "Today"

            date == now.date.plus(DatePeriod(days = 1)) -> weekday

            else -> "$weekday ${date.day} ${month.name.take(
                ABBREVIATION,
            ).lowercase().replaceFirstChar(Char::uppercase)}"
        }
    return "$day, $clock"
}

/** The toast after scheduling, such as "Scheduled for Mon, 8:00". */
internal fun LocalDateTime.scheduledLabel(): String = "Scheduled for $weekday, $clock"

private val LocalDateTime.weekday: String
    get() =
        date.dayOfWeek.name
            .take(ABBREVIATION)
            .lowercase()
            .replaceFirstChar(Char::uppercase)

private val LocalDateTime.clock: String
    get() = "$hour:${minute.toString().padStart(2, '0')}"

private const val ABBREVIATION = 3
