package com.otusfoss.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ShownNameTest {
    @Test
    fun anOrdinaryNameShows() {
        assertEquals("Ada Lovelace", MailSender("Ada Lovelace", "ada@example.org").shownName)
    }

    @Test
    fun aBlankNameShowsTheAddress() {
        assertEquals("ada@example.org", MailSender(" ", "ada@example.org").shownName)
    }

    @Test
    fun aNameThatIsAnotherAddressShowsTheRealAddress() {
        assertEquals("stranger@example.org", MailSender("security@bank.example", "stranger@example.org").shownName)
        assertEquals("other@example.net", shownName("Billing <billing@example.org>", "other@example.net"))
    }

    @Test
    fun aNameThatIsTheSameAddressInAnotherCaseShowsAsBefore() {
        assertEquals(" Ada@Example.org", MailSender(" Ada@Example.org", "ada@example.org").shownName)
    }
}
