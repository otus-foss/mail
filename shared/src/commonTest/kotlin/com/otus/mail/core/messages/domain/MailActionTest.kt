package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailActionTest {
    @Test
    fun noneIsNoSwipe() {
        assertNull(MailAction.decodeSwipe("none", MailAction.Read))
        assertEquals("none", MailAction.encodeSwipe(null))
    }

    @Test
    fun aMissingUnknownOrUnswipeableValueReadsAsTheDefault() {
        listOf(null, "bogus", "move", "rule_for_sender").forEach {
            assertEquals(MailAction.Archive, MailAction.decodeSwipe(it, MailAction.Archive), "Decoding $it")
        }
    }

    @Test
    fun everySwipeableActionReadsBackAsSaved() {
        MailAction.entries.filter { it.swipeable }.forEach {
            assertEquals(it, MailAction.decodeSwipe(MailAction.encodeSwipe(it), default = null))
        }
    }
}
