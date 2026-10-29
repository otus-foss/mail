package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceivedLabelTest {
    private val formatter = DateFormatter(DateFormatPreference.DayMonthYearDots)

    // A Sunday.
    private val today = LocalDate(2026, 10, 11)

    private fun label(
        date: String,
        layout: MailListLayout = MailListLayout(),
    ) = formatter.listDateLabel(ReceivedAt(date, "16:15"), today, layout)

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

    @Test
    fun theFullStyleAlwaysNamesTheDateAndAddsTheTimeToday() {
        val full = MailListLayout(dateStyle = DateStyle.Full)
        assertEquals("11.10.2026 16:15", label("2026-10-11", full))
        assertEquals("10.10.2026", label("2026-10-10", full))
        assertEquals("05.10.2026", label("2026-10-05", full))
    }

    @Test
    fun monthNamesKeepRecentLabelsAndNameTheMonth() {
        val months = MailListLayout(dateStyle = DateStyle.RelativeMonths)
        assertEquals("Yesterday", label("2026-10-10", months))
        assertEquals("Oct 4, 2026", label("2026-10-04", months))
        assertEquals("Jan 1, 2025", label("2025-01-01", months))
        assertEquals("Oct 11, 2026 16:15", label("2026-10-11", MailListLayout(dateStyle = DateStyle.FullMonths)))
    }

    @Test
    fun withoutThisYearOnlyOlderDatesShowTheYear() {
        val noYear = MailListLayout(showThisYear = false)
        assertEquals("04.10", label("2026-10-04", noYear))
        assertEquals("01.01.2025", label("2025-01-01", noYear))
        assertEquals("Oct 4", label("2026-10-04", noYear.copy(dateStyle = DateStyle.RelativeMonths)))
        assertEquals("11.10 16:15", label("2026-10-11", noYear.copy(dateStyle = DateStyle.Full)))
    }

    @Test
    fun eachStyleExplainsItselfWithOneOlderDate() {
        assertEquals("Yesterday, then 20.09.2026", formatter.dateStyleExample(MailListLayout(), today))
        assertEquals(
            "Always the date, like Sep 20",
            formatter.dateStyleExample(MailListLayout(dateStyle = DateStyle.FullMonths, showThisYear = false), today),
        )
    }
}
