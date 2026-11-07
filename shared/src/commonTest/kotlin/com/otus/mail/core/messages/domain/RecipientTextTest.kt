package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RecipientTextTest {
    @Test
    fun readsAddressesAndNamedAddressesSeparatedByCommasOrSemicolons() {
        val expected =
            listOf(
                MailSender("", "a@example.com"),
                MailSender("Bob", "b@example.com"),
                MailSender("", "c@example.com"),
            )
        assertEquals(expected, parseRecipients(" a@example.com, Bob <b@example.com>; c@example.com, "))
    }

    @Test
    fun quotedNamesMayHoldSeparators() {
        assertEquals(listOf(MailSender("Doe, Jo", "jo@example.com")), parseRecipients("\"Doe, Jo\" <jo@example.com>"))
    }

    @Test
    fun anUnfinishedOrEmptyFieldReadsAsNoOne() {
        assertEquals(emptyList(), parseRecipients(" , ;"))
        assertEquals(emptyList(), parseRecipients("Bob <>"))
    }

    @Test
    fun writtenTextReadsBackAsTheSamePeople() {
        val people =
            listOf(
                MailSender("Doe, Jo", "jo@example.com"),
                MailSender("", "a@example.com"),
                MailSender("Ada", "ada@example.com"),
            )
        assertEquals("\"Doe, Jo\" <jo@example.com>, a@example.com, Ada <ada@example.com>", people.recipientText())
        assertEquals(people, parseRecipients(people.recipientText()))
    }
}
