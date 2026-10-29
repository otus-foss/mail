package com.otus.mail.feature.inbox.presentation.components

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
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
            else -> "$weekday $dayAndMonth"
        }
    return "$day, $clock"
}

/** When a scheduled message goes, with its date, as the notice and toast say: "Scheduled for Mon 19 Oct, 8:00". */
internal fun LocalDateTime.scheduledLabel(): String = "Scheduled for $weekday $dayAndMonth, $clock"

private val LocalDateTime.dayAndMonth: String
    get() = date.dayAndMonth

/** A date as the day and the short month, such as "11 Oct". */
internal val LocalDate.dayAndMonth: String
    get() = "$day ${month.name.abbreviated()}"

private val LocalDateTime.weekday: String
    get() = date.dayOfWeek.name.abbreviated()

private val LocalDateTime.clock: String
    get() = "$hour:${minute.toString().padStart(2, '0')}"

/** An enum's name, such as MONDAY, as its first three letters: "Mon". */
private fun String.abbreviated(): String = take(ABBREVIATION).lowercase().replaceFirstChar(Char::uppercase)

private const val ABBREVIATION = 3
