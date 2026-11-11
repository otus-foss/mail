package com.otus.mail

import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.settings.domain.AddressPins
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
                    MailScopeChoice.Address("ada https://mail.example.org/jmap/session", "hi@ruu.by"),
                    MailScopeChoice.Address("ada https://mail.example.org/jmap/session", "test@ruu.by"),
                    MailScopeChoice.Address("bob https://mail.example.com/jmap/session", "bob@example.com"),
                ),
            )
        testStores(context).addressPins.let(::checkNotNull).write(pins.byLogin())
        assertEquals(pins, AddressPins.of(testStores(context).addressPins.let(::checkNotNull).read()))
    }
}
