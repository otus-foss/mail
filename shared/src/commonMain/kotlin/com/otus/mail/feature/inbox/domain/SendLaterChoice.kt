package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** A ready-made time in the Send later sheet. */
enum class SendLaterChoice { ThisEvening, TomorrowMorning, MondayMorning }

/**
 * The ready-made times offered at [now]: this evening before 17:00, tomorrow morning always, and
 * Monday morning from Friday to Sunday, each with its local time.
 */
fun sendLaterChoices(now: LocalDateTime): List<Pair<SendLaterChoice, LocalDateTime>> {
    val today = now.date
    val tomorrow = today.plus(DatePeriod(days = 1))
    val untilMonday = (DayOfWeek.MONDAY.ordinal - today.dayOfWeek.ordinal + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return buildList {
        if (now.time < EVENING_CUTOFF) add(SendLaterChoice.ThisEvening to today.atTime(EVENING))
        add(SendLaterChoice.TomorrowMorning to tomorrow.atTime(MORNING))
        if (today.dayOfWeek in
            WEEKEND_AHEAD
        ) {
            add(SendLaterChoice.MondayMorning to today.plus(DatePeriod(days = untilMonday)).atTime(MORNING))
        }
    }
}

private const val EVENING_HOUR = 18
private const val EVENING_CUTOFF_HOUR = 17
private const val MORNING_HOUR = 8
private val EVENING = LocalTime(EVENING_HOUR, 0)
private val EVENING_CUTOFF = LocalTime(EVENING_CUTOFF_HOUR, 0)
private val MORNING = LocalTime(MORNING_HOUR, 0)
private val WEEKEND_AHEAD = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private const val DAYS_IN_WEEK = 7
