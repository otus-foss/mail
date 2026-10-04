package com.otus.mail.feature.inbox.presentation.components

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarWeeksTest {
    private val weeks = CalendarWeeks(LocalDate(2026, 12, 31))

    @Test
    fun finalWeekStopsAtTodayAcrossWeekMonthAndYearBoundaries() {
        listOf("2026-10-04", "2026-10-05", "2026-01-01", "2024-02-29").forEach { value ->
            val today = LocalDate.parse(value)
            val bounded = CalendarWeeks(today)
            val lastWeek = bounded.startOfWeek(bounded.count - 1)
            val dates = (0..6).map { lastWeek.plus(it, DateTimeUnit.DAY) }.filter { it in bounded.first..bounded.last }
            assertEquals(today, dates.last())
            assertTrue(dates.all { it <= today })
            assertEquals(bounded.count - 1, bounded.indexOf(today.plus(1, DateTimeUnit.DAY)))
            assertEquals(LocalDate(today.year, today.month, 1), bounded.visibleMonth(bounded.count - 1))
        }
    }

    @Test
    fun monthAndYearBoundariesShareAWeekInsteadOfSplittingTheRow() {
        listOf("2026-09-30" to "2026-10-01", "2020-12-31" to "2021-01-01", "2024-02-29" to "2024-03-01")
            .forEach { (last, first) ->
                assertEquals(weeks.indexOf(LocalDate.parse(last)), weeks.indexOf(LocalDate.parse(first)))
            }
    }

    @Test
    fun timelineContainsEveryAllowedDateExactlyOnceInChronologicalOrder() {
        val dates =
            (0 until weeks.count).flatMap { index ->
                val monday = weeks.startOfWeek(index)
                assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
                (0..6).map { monday.plus(it, DateTimeUnit.DAY) }.filter { it in weeks.first..weeks.last }
            }
        assertEquals(weeks.first, dates.first())
        assertEquals(weeks.last, dates.last())
        assertEquals(weeks.first.daysUntil(weeks.last) + 1, dates.size)
        dates.zipWithNext().forEach { (first, second) -> assertEquals(1, first.daysUntil(second)) }
    }

    @Test
    fun monthJumpsShowTheRequestedHeadingEvenWhenTheFirstIsLateInTheWeek() {
        for (year in 1970..2026) {
            for (month in 1..12) {
                val date = LocalDate(year, month, 1)
                assertEquals(date, weeks.visibleMonth(weeks.indexOf(date)))
            }
        }
    }

    @Test
    fun outerWeeksKeepHeadersInsideAllowedYears() {
        assertEquals(LocalDate(1970, 1, 1), weeks.visibleMonth(0))
        assertEquals(LocalDate(2026, 12, 1), weeks.visibleMonth(weeks.count - 1))
        assertEquals(0, weeks.indexOf(LocalDate(1969, 12, 31)))
        assertEquals(weeks.count - 1, weeks.indexOf(LocalDate(2027, 1, 1)))
    }
}
