package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactRemovalTest {
    private class MemoryStore : MemoryMapStore(), ContactStore

    @Test
    fun removingAnAccountForgetsOnlyItsWaitingContactChanges() =
        runTest {
            val logins = InMemoryLoginStore()
            logins.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            logins.save(JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob"), "secret")
            val bobs = ContactEdit.Delete("2", CardKey("bob", "b", "c2"))
            val store = MemoryStore()
            store.write(
                mapOf(
                    ContactStore.EDITS to
                        ContactEdit.encode(listOf(ContactEdit.Delete("1", CardKey("ada", "a", "c1")), bobs)),
                ),
            )

            MailServices(logins, stores = MailDeviceStores(contacts = store)).accounts.remove("ada")

            assertEquals(listOf(bobs), ContactEdit.decode(store.saved[ContactStore.EDITS]))
        }
}
