package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderColors
import com.otus.mail.feature.folders.domain.readColors
import com.otus.mail.feature.folders.domain.writeColors
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderColorRemovalTest {
    private class MemoryStore :
        MemoryMapStore(),
        FolderColorStore

    @Test
    fun removingAnAccountForgetsOnlyItsFolderColors() =
        runTest {
            val logins = InMemoryLoginStore()
            val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
            val bob = JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob")
            logins.save(ada, "secret")
            logins.save(bob, "secret")
            val adaClients = MailFolder("ada", "M1", "Clients")
            val bobClients = MailFolder("bob", "M1", "Clients")
            val store = MemoryStore()
            store.writeColors(FolderColors().with(listOf(adaClients, bobClients), "Yellow"))

            MailServices(logins, stores = MailDeviceStores(FolderStores(colors = store))).accounts.remove("ada")

            assertEquals(FolderColors().with(listOf(bobClients), "Yellow"), store.readColors())
        }
}
