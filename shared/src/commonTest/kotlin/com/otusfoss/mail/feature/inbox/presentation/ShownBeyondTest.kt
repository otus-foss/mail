package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.messages.domain.FilterSort
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals

class ShownBeyondTest {
    private fun message(
        id: String,
        day: Int,
    ) = feedMessage(id).copy(received = ReceivedAt("2026-10-${day.toString().padStart(2, '0')}", "12:00"))

    @Test
    fun rowsPastThePageStayAndRowsWithinItGo() {
        val shown = listOf(message("new", 20), message("dropped", 15), message("old", 5))
        val page = listOf(message("new", 20), message("edge", 10))
        assertEquals(listOf("new", "edge", "old"), page.withShownBeyond(shown, FilterSort.NewestFirst).map { it.id })
    }

    @Test
    fun oldestFirstKeepsTheNewerRows() {
        val shown = listOf(message("early", 1), message("late", 25))
        val page = listOf(message("early", 1), message("mid", 10))
        assertEquals(listOf("early", "mid", "late"), page.withShownBeyond(shown, FilterSort.OldestFirst).map { it.id })
    }

    @Test
    fun anEmptyPageKeepsEverythingShown() {
        val shown = listOf(message("a", 3))
        assertEquals(shown, emptyList<MailMessage>().withShownBeyond(shown, FilterSort.NewestFirst))
    }
}
