package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceivedLabelTest {
    private val formatter = DateFormatter(DateFormatPreference.DayMonthYearDots)

    // A Sunday.
    private val today = LocalDate(2026, 10, 11)

    private fun label(date: String) = formatter.receivedLabel(ReceivedAt(date, "16:15"), today)

    @Test
    fun recentMailNamesTheTimeTheDayOrTheWeekday() {
        assertEquals("16:15", label("2026-10-11"))
        assertEquals("Yesterday", label("2026-10-10"))
        assertEquals("Mon", label("2026-10-05"))
    }

    @Test
    fun olderMailUsesTheSavedDateFormat() {
        assertEquals("04.10.2026", label("2026-10-04"))
        assertEquals("01.01.2025", label("2025-01-01"))
    }
}
