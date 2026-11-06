package com.otus.mail

import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.settings.domain.AddressPins
import com.otus.mail.core.settings.domain.readPins
import com.otus.mail.core.settings.domain.writePins
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddressPinStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun pinsSurviveReopeningTheStore() {
        // Login ids hold a space and the session URL's colons.
        val pins =
            AddressPins(
                listOf(
                    MailScopeChoice.Address("ada https://mail.example.org/jmap/session", "hi@design.ch"),
                    MailScopeChoice.Address("ada https://mail.example.org/jmap/session", "test@design.ch"),
                    MailScopeChoice.Address("bob https://mail.example.com/jmap/session", "bob@example.com"),
                ),
            )
        testStores(context).addressPins.let(::checkNotNull).writePins(pins)
        assertEquals(pins, testStores(context).addressPins.let(::checkNotNull).readPins())
    }
}
