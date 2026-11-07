package com.otus.mail.core.settings.domain

import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressPinsTest {
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
        val store = DeviceStore(MemoryMapStore(), AddressPinStore)
        val pins =
            AddressPins(
                listOf(
                    MailScopeChoice.Address(ada, "hi@design.ch"),
                    MailScopeChoice.Address(ada, "test@design.ch"),
                    MailScopeChoice.Address(bob, "hi@company.com"),
                ),
            )
        store.write(pins.byLogin())
        assertEquals(pins, AddressPins.of(store.read()))
    }
}
