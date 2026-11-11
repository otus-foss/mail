package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAddressesTest {
    private val design =
        AccountAddresses("design", "Design", listOf("hi@ruu.by", "test@ruu.by", "info@company.example"))

    @Test
    fun addressesGroupByDomainInOrderOfFirstAppearance() {
        assertEquals(
            listOf(
                DomainGroup(
                    "ruu.by",
                    listOf(
                        MailScopeChoice.Address("design", "hi@ruu.by"),
                        MailScopeChoice.Address("design", "test@ruu.by"),
                    ),
                ),
                DomainGroup("company.example", listOf(MailScopeChoice.Address("design", "info@company.example"))),
            ),
            domainGroups(listOf(design)),
        )
    }

    @Test
    fun searchFindsAddressesContainingTheQueryIgnoringCase() {
        assertEquals(
            listOf(MailScopeChoice.Address("design", "info@company.example")),
            searchAddresses(listOf(design), " COMPANY "),
        )
    }
}
