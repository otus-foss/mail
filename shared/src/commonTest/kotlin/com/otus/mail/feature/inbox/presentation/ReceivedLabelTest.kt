package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateStyle
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

    private fun listLabel(
        date: String,
        style: DateStyle,
    ) = formatter.listDateLabel(ReceivedAt(date, "16:15"), today, style)

    @Test
    fun theRelativeStyleKeepsTheRowLabel() {
        listOf("2026-10-11", "2026-10-10", "2026-10-05", "2026-10-04").forEach { date ->
            assertEquals(label(date), listLabel(date, DateStyle.Relative))
        }
    }

    @Test
    fun theFullStyleAlwaysNamesTheDateAndAddsTheTimeToday() {
        assertEquals("11.10.2026 16:15", listLabel("2026-10-11", DateStyle.Full))
        assertEquals("10.10.2026", listLabel("2026-10-10", DateStyle.Full))
        assertEquals("05.10.2026", listLabel("2026-10-05", DateStyle.Full))
    }
}
