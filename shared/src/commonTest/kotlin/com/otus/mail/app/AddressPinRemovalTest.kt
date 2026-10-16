package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.AddressPinStore
import com.otus.mail.feature.inbox.domain.AddressPins
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.readPins
import com.otus.mail.feature.inbox.domain.writePins
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressPinRemovalTest {
    private class MemoryStore : AddressPinStore {
        var saved = emptyMap<String, String>()

        override fun read() = saved

        override fun write(pins: Map<String, String>) {
            saved = pins
        }
    }

    @Test
    fun removingAnAccountForgetsOnlyItsPins() =
        runTest {
            val logins = InMemoryLoginStore()
            logins.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            logins.save(JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob"), "secret")
            val bobPin = MailScopeChoice.Address("bob", "bob@example.com")
            val store = MemoryStore()
            store.writePins(AddressPins(listOf(MailScopeChoice.Address("ada", "ada@example.org"), bobPin)))

            MailServices(logins, stores = MailDeviceStores(addressPins = store)).accounts.remove("ada")

            assertEquals(AddressPins(listOf(bobPin)), store.readPins())
        }
}
