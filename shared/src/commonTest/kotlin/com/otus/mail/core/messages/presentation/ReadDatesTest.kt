package com.otus.mail.core.messages.presentation

import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.ReceivedAt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadDatesTest {
    private val today = LocalDate(2026, 10, 19)

    private val dates = DateFormatter(DateFormatPreference.DayMonthYearDots)

    private fun short(date: String) = dates.shortLabel(ReceivedAt(date, "09:14"), today)

    @Test
    fun shortLabelShowsTheTimeToday() = assertEquals("09:14", short("2026-10-19"))

    @Test
    fun shortLabelSaysYesterday() = assertEquals("Yesterday", short("2026-10-18"))

    @Test
    fun shortLabelShowsTheWeekdayWithinAWeek() {
        assertEquals("Wed", short("2026-10-14"))
        assertEquals("Tue", short("2026-10-13"))
    }

    @Test
    fun shortLabelShowsDayAndMonthAfterAWeek() = assertEquals("12 Oct", short("2026-10-12"))

    @Test
    fun shortLabelAddsTheYearOfAnotherYear() = assertEquals("31 Dec 2025", short("2025-12-31"))

    @Test
    fun shortLabelShowsAFutureDateAsADate() = assertEquals("2 Nov", short("2026-11-02"))

    @Test
    fun fullLabelShowsDateYearAndTime() =
        assertEquals("12 Oct 2026, 09:14", dates.fullLabel(ReceivedAt("2026-10-12", "09:14")))

    @Test
    fun positionDetailCountsFromOne() {
        assertEquals("1/6 · 12 Oct", positionDetail(0, 6, "12 Oct"))
        assertEquals("5/6 · 10 Oct", positionDetail(4, 6, "10 Oct"))
    }
}
