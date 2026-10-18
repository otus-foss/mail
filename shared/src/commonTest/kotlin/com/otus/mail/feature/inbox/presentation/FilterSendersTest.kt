package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterSendersTest {
    private val messages =
        listOf("sam@x.org", "lee@x.org", "sam@x.org", "kim@x.org", "Sam@x.org").mapIndexed { index, address ->
            MailMessage(
                "$index",
                MailSender(address.substringBefore('@'), address),
                "",
                "",
                ReceivedAt("2026-10-18", "09:00"),
                MailLocation("Inbox", "Ada"),
            )
        }

    @Test
    fun chosenSendersComeFirstThenTheMostFrequent() {
        val senders = filterSenders(messages, setOf("kim@x.org", "new@y.org"), limit = 3)
        assertEquals(listOf("kim@x.org", "new@y.org", "sam@x.org"), senders.map { it.address })
        assertEquals("kim", senders.first().name)
        assertEquals("", senders[1].name, "An address no loaded mail came from shows bare")
    }
}
