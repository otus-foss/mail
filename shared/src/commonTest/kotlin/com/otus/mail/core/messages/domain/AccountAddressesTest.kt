package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAddressesTest {
    private val design =
        AccountAddresses("design", "Design", listOf("hi@design.ch", "test@design.ch", "info@company.com"))

    @Test
    fun addressesGroupByDomainInOrderOfFirstAppearance() {
        assertEquals(
            listOf(
                DomainGroup(
                    "design.ch",
                    listOf(
                        MailScopeChoice.Address("design", "hi@design.ch"),
                        MailScopeChoice.Address("design", "test@design.ch"),
                    ),
                ),
                DomainGroup("company.com", listOf(MailScopeChoice.Address("design", "info@company.com"))),
            ),
            domainGroups(listOf(design)),
        )
    }

    @Test
    fun searchFindsAddressesContainingTheQueryIgnoringCase() {
        assertEquals(
            listOf(MailScopeChoice.Address("design", "info@company.com")),
            searchAddresses(listOf(design), " COMPANY "),
        )
    }
}
