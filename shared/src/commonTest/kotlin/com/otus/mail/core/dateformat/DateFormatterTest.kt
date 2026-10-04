package com.otus.mail.core.dateformat

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateFormatterTest {
    @Test
    fun explicitFormatsPreserveDistinctDayAndMonthAndPadNumbers() {
        val date = LocalDate(2026, 3, 9)
        val cases =
            mapOf(
                DateFormatPreference.DayMonthYearDots to "09.03.2026",
                DateFormatPreference.DayMonthYearSlashes to "09/03/2026",
                DateFormatPreference.MonthDayYearDots to "03.09.2026",
                DateFormatPreference.MonthDayYearSlashes to "03/09/2026",
                DateFormatPreference.YearMonthDayDashes to "2026-03-09",
                DateFormatPreference.YearMonthDaySlashes to "2026/03/09",
            )
        cases.forEach { (preference, expected) ->
            assertEquals(expected, DateFormatter(preference, "en-US").format(date))
            assertEquals(expected, DateFormatter(preference, "de-DE").format(date))
        }
    }

    @Test
    fun yearBoundaryUsesCalendarYearRatherThanWeekYear() {
        val formatter = DateFormatter(DateFormatPreference.DayMonthYearSlashes)
        assertEquals("31/12/2020", formatter.formatIsoDate("2020-12-31"))
        assertEquals("01/01/2021", formatter.formatIsoDate("2021-01-01"))
        assertEquals("29/02/2024", formatter.formatIsoDate("2024-02-29"))
    }

    @Test
    fun rangeDisplayKeepsBothYearsAndDoesNotChangeDomainDates() {
        val start = LocalDate(2020, 12, 30)
        val end = LocalDate(2021, 1, 3)
        val formatter = DateFormatter(DateFormatPreference.MonthDayYearDots)
        assertEquals("12.30.2020 → 01.03.2021", formatter.formatRange(start, end))
        assertEquals("2020-12-30", start.toString())
        assertEquals("2021-01-03", end.toString())
    }

    @Test
    fun missingOrUnrecognizedStoredPreferenceFallsBackToDeviceLocale() {
        assertEquals(DateFormatPreference.DeviceLocale, DateFormatPreference.fromStorageValue(null))
        assertEquals(DateFormatPreference.DeviceLocale, DateFormatPreference.fromStorageValue("old-unknown-value"))
        assertEquals(
            DateFormatPreference.DayMonthYearSlashes,
            DateFormatPreference.fromStorageValue("dmy-slashes"),
        )
    }

    @Test
    fun malformedIsoInputIsRejectedRatherThanGuessedAsAnotherDateOrder() {
        val formatter = DateFormatter(DateFormatPreference.DayMonthYearDots)
        assertFailsWith<IllegalArgumentException> { formatter.formatIsoDate("31/12/2020") }
        assertFailsWith<IllegalArgumentException> { formatter.formatIsoDate("2023-02-29") }
    }
}
