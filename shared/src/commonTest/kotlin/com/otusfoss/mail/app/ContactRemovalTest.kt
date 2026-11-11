package com.otusfoss.mail.app

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactEdit
import com.otusfoss.mail.feature.contacts.domain.ContactStore
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactRemovalTest {
    @Test
    fun removingAnAccountForgetsOnlyItsWaitingContactChanges() =
        runTest {
            val logins = InMemoryLoginStore()
            logins.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            logins.save(JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob"), "secret")
            val bobs = ContactEdit.Delete("2", CardKey("bob", "b", "c2"))
            val adas = ContactEdit.Delete("1", CardKey("ada", "a", "c1"))
            val store = MemoryMapStore(mapOf(ContactStore.EDITS.entry(listOf(adas, bobs))))

            MailServices(logins, stores = MailDeviceStores(contacts = DeviceStore(store, ContactStore)))
                .accounts
                .remove("ada")

            assertEquals(listOf(bobs), ContactStore.EDITS.of(store.saved))
        }
}
