package com.otusfoss.mail.feature.compose.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
        if (today.dayOfWeek in WEEKEND_AHEAD) {
            add(SendLaterChoice.MondayMorning to today.plus(DatePeriod(days = untilMonday)).atTime(MORNING))
        }
    }
}

/**
 * The first time Pick date and time offers at [now]: the next five-minute step at least a minute
 * ahead, so nothing in the past ever shows. It can fall on the next day.
 */
fun earliestPick(now: LocalDateTime): LocalDateTime {
    val ahead = now.toInstant(TimeZone.UTC) + 1.minutes
    val minutes = ahead.epochSeconds / SECONDS_PER_MINUTE
    val step = (minutes + PICK_STEP_MINUTES - 1) / PICK_STEP_MINUTES * PICK_STEP_MINUTES
    return Instant.fromEpochSeconds(step * SECONDS_PER_MINUTE).toLocalDateTime(TimeZone.UTC)
}

/** The times of [date] between [earliest] and [latest]; the whole day on any date in between. */
fun pickableTimes(
    date: LocalDate,
    earliest: LocalDateTime,
    latest: LocalDateTime,
): ClosedRange<LocalTime> {
    val start = if (date == earliest.date) earliest.time else LocalTime(hour = 0, minute = 0)
    val end = if (date == latest.date) latest.time else LocalTime(LAST_HOUR, LAST_MINUTE)
    return start..end
}

private const val PICK_STEP_MINUTES = 5L
private const val SECONDS_PER_MINUTE = 60L
private const val LAST_HOUR = 23
private const val LAST_MINUTE = 59
private const val EVENING_HOUR = 18
private const val EVENING_CUTOFF_HOUR = 17
private const val MORNING_HOUR = 8
private val EVENING = LocalTime(EVENING_HOUR, minute = 0)
private val EVENING_CUTOFF = LocalTime(EVENING_CUTOFF_HOUR, minute = 0)
private val MORNING = LocalTime(MORNING_HOUR, minute = 0)
private val WEEKEND_AHEAD = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private const val DAYS_IN_WEEK = 7
