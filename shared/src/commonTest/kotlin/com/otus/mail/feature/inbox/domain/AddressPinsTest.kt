package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressPinsTest {
    private class MemoryStore : AddressPinStore {
        var saved = emptyMap<String, String>()

        override fun read() = saved

        override fun write(pins: Map<String, String>) {
            saved = pins
        }
    }

    // Login ids hold a space and the session URL's colons.
    private val ada = "ada https://mail.example.org/jmap/session"
    private val bob = "bob https://mail.example.com/jmap/session"

    @Test
    fun togglingPinsAndThenUnpinsAnAddressIgnoringCase() {
        val pinned = AddressPins().toggled(MailScopeChoice.Address(ada, "hi@design.ch"))
        assertTrue(MailScopeChoice.Address(ada, "HI@design.ch") in pinned)
        assertFalse(MailScopeChoice.Address(bob, "hi@design.ch") in pinned)
        assertEquals(AddressPins(), pinned.toggled(MailScopeChoice.Address(ada, "Hi@Design.ch")))
    }

    @Test
    fun pinsSurviveTheStore() {
        val store = MemoryStore()
        val pins =
            AddressPins(
                listOf(
                    MailScopeChoice.Address(ada, "hi@design.ch"),
                    MailScopeChoice.Address(ada, "test@design.ch"),
                    MailScopeChoice.Address(bob, "hi@company.com"),
                ),
            )
        store.writePins(pins)
        assertEquals(pins, store.readPins())
    }

    @Test
    fun forgettingAnAccountKeepsOnlyTheOtherAccountsPins() {
        val store = MemoryStore()
        store.writePins(
            AddressPins(
                listOf(MailScopeChoice.Address(ada, "hi@design.ch"), MailScopeChoice.Address(bob, "hi@company.com")),
            ),
        )
        store.forgetAccount(ada)
        assertEquals(AddressPins(listOf(MailScopeChoice.Address(bob, "hi@company.com"))), store.readPins())
    }
}
