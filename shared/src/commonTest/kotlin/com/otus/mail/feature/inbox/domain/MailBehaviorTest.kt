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
}
