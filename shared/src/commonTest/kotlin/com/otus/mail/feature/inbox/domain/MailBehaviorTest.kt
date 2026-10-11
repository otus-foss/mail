package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailBehaviorTest {
    @Test
    fun readLaterIsASwipeChoiceOnlyWhileItIsOn() {
        assertFalse(MessageSwipe.ReadLater in MailBehavior().swipeChoices)
        assertTrue(MessageSwipe.ReadLater in MailBehavior(readLater = true).swipeChoices)
    }

    @Test
    fun turningReadLaterOffReturnsItsSwipesToTheirDefaults() {
        val on = MailBehavior(readLater = true, startToEnd = MessageSwipe.ReadLater, endToStart = MessageSwipe.Trash)
        assertEquals(on, on.withReadLater(true))
        assertEquals(MailBehavior(endToStart = MessageSwipe.Trash), on.withReadLater(false))
    }

    @Test
    fun readLaterAddsItsPillToTheEndOfTheRowWhileItIsOn() {
        val flagged = MailBehavior(pills = listOf(InboxPill.Filter.Flagged))
        val on = flagged.withReadLater(true)
        assertEquals(listOf(InboxPill.Filter.Flagged, InboxPill.ReadLater), on.pills)
        val reordered = on.copy(pills = on.pills.reversed())
        assertEquals(reordered, reordered.withReadLater(true), "Turning it on again keeps the row as it is")
        assertEquals(listOf(InboxPill.Filter.Flagged), on.withReadLater(false).pills)
    }
}
