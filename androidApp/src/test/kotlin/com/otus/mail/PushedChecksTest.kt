package com.otus.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushedChecksTest {
    @Test
    fun pushesDuringACheckQueueOnlyOneMore() {
        val checks = PushedChecks()
        checks.received()
        assertTrue(checks.start(pushed = true))
        repeat(5) { checks.received() }
        val runs = List(5) { checks.start(pushed = true) }
        assertEquals(listOf(true, false, false, false, false), runs)
    }

    @Test
    fun aPeriodicCheckAlwaysRunsAndCoversEarlierPushes() {
        val checks = PushedChecks()
        checks.received()
        assertTrue(checks.start(pushed = false))
        assertFalse(checks.start(pushed = true))
        assertTrue(checks.start(pushed = false))
    }

    @Test
    fun theFirstPushedCheckOfAProcessRuns() = assertTrue(PushedChecks().start(pushed = true))
}
