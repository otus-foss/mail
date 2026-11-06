package com.otus.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAddressesTest {
    private val design =
        AccountAddresses.of(
            "design",
            "Design",
            username = "hi@design.ch",
            identities = listOf("HI@design.ch", " test@design.ch ", "*@design.ch", "info@company.com"),
        )

    @Test
    fun anAccountHasItsSignInThenItsIdentitiesOnceEachWithoutWildcards() {
        assertEquals(listOf("hi@design.ch", "test@design.ch", "info@company.com"), design.addresses)
    }

    @Test
    fun aSignInNameThatIsNoAddressIsLeftOut() {
        assertEquals(
            listOf("ada@example.org"),
            AccountAddresses.of("ada", "Ada", "ada", listOf("ada@example.org")).addresses,
        )
    }

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
