package com.otusfoss.mail.feature.contacts.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FrequentPeopleTest {
    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val me = "ada@example.org"

    private fun sent(
        vararg to: String,
        copied: List<String> = emptyList(),
        at: Instant = now,
    ) = MailTrace(at, from = listOf(me), to = to.toList(), copied = copied)

    private fun received(
        from: String,
        at: Instant = now,
    ) = MailTrace(at, from = listOf(from), to = listOf(me))

    private fun scores(
        sent: List<MailTrace>,
        received: List<MailTrace> = emptyList(),
    ) = frequencyScores(MailTraces(sent, received), now)

    @Test
    fun aSentEmailGivesItsOnlyRecipientTheFullToWeight() {
        assertEquals(10.0, scores(listOf(sent("jana@example.org")))["jana@example.org"])
    }

    @Test
    fun copiedRecipientsCountLessThanTo() {
        val result = scores(listOf(sent("jana@example.org", copied = listOf("omar@example.org"))))

        assertTrue(result.getValue("jana@example.org") > result.getValue("omar@example.org"))
    }

    @Test
    fun aLongRecipientListCountsLittleForEach() {
        val crowd = (1..100).map { "p$it@example.org" }.toTypedArray()

        val result = scores(listOf(sent(*crowd), sent("jana@example.org")))

        assertEquals(1.0, result.getValue("p1@example.org"), 1e-9)
        assertEquals(10.0, result.getValue("jana@example.org"))
    }

    @Test
    fun receivedMailCountsOnlyFromPeopleTheUserWroteTo() {
        val result =
            scores(
                listOf(sent("jana@example.org")),
                listOf(received("jana@example.org"), received("noreply@github.com"), received("noreply@github.com")),
            )

        assertEquals(11.0, result["jana@example.org"])
        assertNull(result["noreply@github.com"])
    }

    @Test
    fun sendingOnceOutweighsManyReceivedMessages() {
        val result =
            scores(
                listOf(sent("jana@example.org"), sent("omar@example.org")),
                List(5) { received("omar@example.org") },
            )

        assertEquals(10.0, result["jana@example.org"])
        assertEquals(15.0, result["omar@example.org"])
    }

    @Test
    fun anEmailCountsHalfAsMuchEveryNinetyDays() {
        val result = scores(listOf(sent("jana@example.org", at = now - 90.days)))

        assertEquals(5.0, result.getValue("jana@example.org"), 1e-9)
    }

    @Test
    fun theUsersOwnAddressesNeverScore() {
        val result =
            scores(
                listOf(
                    sent(me, "jana@example.org"),
                    MailTrace(now, listOf("ada@work.example"), listOf("Ada@Work.example")),
                ),
            )

        assertNull(result[me])
        assertNull(result["ada@work.example"])
    }

    @Test
    fun addressesAreCountedWithoutCase() {
        val result = scores(listOf(sent("Jana@Example.org"), sent("jana@example.org ")))

        assertEquals(mapOf("jana@example.org" to 20.0), result)
    }
}
