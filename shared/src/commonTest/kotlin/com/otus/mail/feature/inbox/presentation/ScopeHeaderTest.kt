package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.AccountAddresses
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.settings.presentation.title
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeHeaderTest {
    private val design = AccountAddresses("design", "Design", listOf("hi@design.ch", "test@design.ch", "hi@other.com"))
    private val company = AccountAddresses("company", "Company", listOf("hi@company.com", "info@company.com"))

    @Test
    fun allMailNamesNoCountAndShowsEachAccountOrEachDomainOfOne() {
        val several = scopeHeader(MailScopeChoice.All, listOf(design, company))
        assertEquals("All mail" to null, several.title to several.subtitle)
        assertEquals(listOf("D", "C"), several.avatars.map { it.initials })
        assertEquals(listOf("D", "O"), scopeHeader(MailScopeChoice.All, listOf(design)).avatars.map { it.initials })
        assertEquals("No accounts yet", scopeHeader(MailScopeChoice.All, emptyList()).subtitle)
    }

    @Test
    fun anAccountShowsItsNameOverItsAddressCount() {
        val header = scopeHeader(MailScopeChoice.Account("design"), listOf(design, company))
        assertEquals("Design" to "3 addresses", header.title to header.subtitle)
    }

    @Test
    fun anAddressShowsItsAccountBelowIt() {
        val header = scopeHeader(MailScopeChoice.Address("company", "hi@company.com"), listOf(design, company))
        assertEquals("hi@company.com" to "Company", header.title to header.subtitle)
    }

    @Test
    fun allMailSummaryCountsAccountsOnlyWhenThereAreSeveral() {
        assertEquals("3 addresses", allMailSummary(listOf(design)))
        assertEquals("2 accounts · 5 addresses", allMailSummary(listOf(design, company)))
        assertEquals("1 address", addressCount(1))
    }
}
