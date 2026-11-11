package com.otusfoss.mail.feature.compose.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SendLaterTest {
    @Test
    fun aFridayAfternoonOffersTheEveningTomorrowAndMonday() {
        val choices = sendLaterChoices(LocalDateTime(2026, 10, 16, 14, 30))
        assertEquals(
            listOf(
                SendLaterChoice.ThisEvening to LocalDateTime(2026, 10, 16, 18, 0),
                SendLaterChoice.TomorrowMorning to LocalDateTime(2026, 10, 17, 8, 0),
                SendLaterChoice.MondayMorning to LocalDateTime(2026, 10, 19, 8, 0),
            ),
            choices,
        )
    }

    @Test
    fun theEveningGoesAtFiveAndMondayOnlyComesNearTheWeekend() {
        val tuesday = sendLaterChoices(LocalDateTime(2026, 10, 13, 17, 0))
        assertEquals(listOf(SendLaterChoice.TomorrowMorning), tuesday.map { it.first })
        val sunday = sendLaterChoices(LocalDateTime(2026, 10, 18, 9, 0))
        assertEquals(LocalDateTime(2026, 10, 19, 8, 0), sunday.last().second)
    }

    @Test
    fun pickingStartsAtTheNextStepAheadAndCanMoveToTomorrow() {
        assertEquals(LocalDateTime(2026, 10, 17, 15, 0), earliestPick(LocalDateTime(2026, 10, 17, 14, 56)))
        assertEquals(LocalDateTime(2026, 10, 17, 15, 5), earliestPick(LocalDateTime(2026, 10, 17, 15, 0)))
        assertEquals(LocalDateTime(2026, 10, 18, 0, 0), earliestPick(LocalDateTime(2026, 10, 17, 23, 57)))
    }

    @Test
    fun todayStartsAtTheEarliestTimeAndTheLastDayEndsAtTheLimit() {
        val earliest = LocalDateTime(2026, 10, 17, 15, 0)
        val latest = LocalDateTime(2026, 10, 19, 9, 30)
        assertEquals(LocalTime(15, 0)..LocalTime(23, 59), pickableTimes(earliest.date, earliest, latest))
        assertEquals(LocalTime(0, 0)..LocalTime(23, 59), pickableTimes(LocalDate(2026, 10, 18), earliest, latest))
        assertEquals(LocalTime(0, 0)..LocalTime(9, 30), pickableTimes(latest.date, earliest, latest))
    }
}
