package com.otus.mail.core.dateformat

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeDateFormattingTest {
    private val formatter = DateFormatter(DateFormatPreference.DayMonthYearSlashes)

    @Test
    fun relativeLabelsCrossYearBoundaryAndOtherDatesKeepPreferredFormat() {
        val today = LocalDate(2026, 1, 1)
        assertEquals("Today", formatter.formatRelative(today, today))
        assertEquals("Yesterday", formatter.formatRelative(LocalDate(2025, 12, 31), today))
        assertEquals("30/12/2025", formatter.formatRelative(LocalDate(2025, 12, 30), today))
        assertEquals("02/01/2026", formatter.formatRelative(LocalDate(2026, 1, 2), today))
    }

    @Test
    fun yesterdayIncludesLeapDayWhenTodayIsMarchFirst() {
        assertEquals("Yesterday", formatter.formatRelative(LocalDate(2024, 2, 29), LocalDate(2024, 3, 1)))
        assertEquals("Yesterday", formatter.formatRelative(LocalDate(2025, 2, 28), LocalDate(2025, 3, 1)))
    }

    @Test
    fun rangeFormatsEachEndpointAndLeavesIsoDatesUnchanged() {
        val today = LocalDate(2026, 10, 4)
        val yesterday = LocalDate(2026, 10, 3)
        assertEquals("Yesterday → Today", formatter.formatRelativeRange(yesterday, today, today))
        assertEquals("Yesterday–Today", formatter.formatRelativeRange(yesterday, today, today, "–"))
        assertEquals("01/10/2026 → Today", formatter.formatRelativeRange(LocalDate(2026, 10, 1), today, today))
        assertEquals("2026-10-03", yesterday.toString())
        assertEquals("04/10/2026", formatter.format(today))
    }
}
