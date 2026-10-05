package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.core.dateformat.DateFormatPreference
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.InboxSearch
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFilterLabelTest {
    private val today = LocalDate(2026, 10, 5)
    private val iso = DateFormatter(DateFormatPreference.YearMonthDayDashes, "en-US")

    @Test
    fun presetsStayShortAcrossDateFormatsAndMonthBoundaries() {
        val slashes = DateFormatter(DateFormatPreference.MonthDayYearSlashes, "en-US")
        val week = InboxSearch(dateStart = "2026-09-29", dateEnd = "2026-10-05")
        val month = InboxSearch(dateStart = "2026-09-06", dateEnd = "2026-10-05")
        for (formatter in listOf(iso, slashes)) {
            assertEquals("This week", dateFilterLabel(week, formatter, today))
            assertEquals("This month", dateFilterLabel(month, formatter, today))
        }
    }

    @Test
    fun olderRangesOfTheSameLengthRetainTheChosenDateFormat() {
        val older = InboxSearch(dateStart = "2026-09-21", dateEnd = "2026-09-27")
        assertEquals("2026-09-21–2026-09-27", dateFilterLabel(older, iso, today))
        val dots = DateFormatter(DateFormatPreference.DayMonthYearDots, "en-US")
        assertEquals("21.09.2026–27.09.2026", dateFilterLabel(older, dots, today))
    }

    @Test
    fun customRangesAndSingleDatesKeepRelativeLabels() {
        assertEquals("Date", dateFilterLabel(InboxSearch(), iso, today))
        assertEquals("Today", dateFilterLabel(InboxSearch(dateStart = "2026-10-05"), iso, today))
        assertEquals("Yesterday", dateFilterLabel(InboxSearch(dateStart = "2026-10-04"), iso, today))
        val custom = InboxSearch(dateStart = "2026-10-01", dateEnd = "2026-10-05")
        assertEquals("2026-10-01–Today", dateFilterLabel(custom, iso, today))
    }
}
