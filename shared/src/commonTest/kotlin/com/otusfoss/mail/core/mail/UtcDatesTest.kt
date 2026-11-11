package com.otusfoss.mail.core.mail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class UtcDatesTest {
    @Test
    fun aLeapSecondReceivedAtIsADate() {
        assertEquals(
            Instant.parse("2016-12-31T23:59:59.999999999Z"),
            EmailObject("m1", receivedAt = "2016-12-31T23:59:60Z").received,
        )
        assertNull(EmailObject("m2", receivedAt = "yesterday").received)
    }

    @Test
    fun filterDatesDropTheFraction() {
        assertEquals("2026-10-14T08:00:00Z", Instant.parse("2026-10-14T08:00:00.75Z").utcDate())
    }
}
