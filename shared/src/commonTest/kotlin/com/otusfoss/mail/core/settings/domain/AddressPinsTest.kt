package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.MemoryMapStore
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
        val pinned = AddressPins().toggled(MailScopeChoice.Address(ada, "hi@ruu.by"))
        assertTrue(MailScopeChoice.Address(ada, "HI@ruu.by") in pinned)
        assertFalse(MailScopeChoice.Address(bob, "hi@ruu.by") in pinned)
        assertEquals(AddressPins(), pinned.toggled(MailScopeChoice.Address(ada, "Hi@Ruu.by")))
    }

    @Test
    fun pinsSurviveTheStore() {
        val store = DeviceStore(MemoryMapStore(), AddressPinStore)
        val pins =
            AddressPins(
                listOf(
                    MailScopeChoice.Address(ada, "hi@ruu.by"),
                    MailScopeChoice.Address(ada, "test@ruu.by"),
                    MailScopeChoice.Address(bob, "hi@company.example"),
                ),
            )
        store.write(pins.byLogin())
        assertEquals(pins, AddressPins.of(store.read()))
    }
}
