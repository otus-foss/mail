package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.LocalDateTime
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
}
