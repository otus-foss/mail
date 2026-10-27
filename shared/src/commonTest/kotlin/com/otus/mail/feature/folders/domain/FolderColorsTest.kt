package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderColorsTest {
    /** Keeps names as a platform store would, by storage value. */
    private class MemoryStore(
        var saved: Map<String, String> = emptyMap(),
    ) : FolderColorStore {
        override fun read() = saved

        override fun write(values: Map<String, String>) {
            saved = values
        }
    }

    private val adaClients = MailFolder("ada login", "M1", "Clients")
    private val bobClients = MailFolder("bob login", "M1", "Clients")

    private fun shared(vararg accounts: AccountFolders) = sharedFolders(accounts.toList()).single()

    @Test
    fun colorsSurviveTheStoreKeyedByAccountAndMailbox() {
        val store = MemoryStore()
        val colors = FolderColors().with(listOf(adaClients), "Red").with(listOf(bobClients), "Blue")
        store.writeColors(colors)
        assertEquals(colors, store.readColors())
        assertEquals("Red", store.readColors().of(adaClients))
        assertEquals("Blue", store.readColors().of(bobClients))
    }

    @Test
    fun unreadableEntriesReadAsTheDefault() {
        val store = MemoryStore(mapOf("M1" to "Red", ":ada" to "Red", "M1:ada login" to " ", "M2:ada login" to "Teal"))
        assertEquals(mapOf(FolderKey("ada login", "M2") to "Teal"), store.readColors().names)
        assertNull(store.readColors().of(adaClients))
    }

    @Test
    fun aSharedFolderShowsTheFirstAccountsColorAndSettingItColorsEveryAccount() {
        val folder =
            shared(
                AccountFolders("ada login", "Ada", listOf(adaClients)),
                AccountFolders("bob login", "Bob", listOf(bobClients)),
            )
        val disagreeing = FolderColors().with(listOf(adaClients), "Red").with(listOf(bobClients), "Blue")
        assertEquals("Red", disagreeing.of(folder))
        assertEquals("Blue", disagreeing.of(bobClients))

        val grouped = FolderColors().with(folder.folders, "Green")
        assertEquals(listOf("Green", "Green"), folder.folders.map(grouped::of))
        assertEquals(emptyMap(), grouped.with(folder.folders, null).names)
    }

    @Test
    fun unlinkingASharedFolderKeepsItsColorOnEachAccount() {
        val folder =
            shared(
                AccountFolders("ada login", "Ada", listOf(adaClients)),
                AccountFolders("bob login", "Bob", listOf(bobClients)),
            )
        val colors = FolderColors().with(folder.folders, "Purple")
        val unlinked =
            sharedFolders(
                listOf(
                    AccountFolders("ada login", "Ada", listOf(adaClients)),
                    AccountFolders("bob login", "Bob", listOf(bobClients.copy(name = "Customers"))),
                ),
            )
        assertEquals(listOf("Purple", "Purple"), unlinked.map(colors::of))
    }

    @Test
    fun removingAnAccountForgetsOnlyItsColors() {
        val store = MemoryStore()
        store.writeColors(FolderColors().with(listOf(adaClients), "Red").with(listOf(bobClients), "Blue"))
        store.forgetAccount("ada login")
        assertEquals(mapOf(bobClients.key to "Blue"), store.readColors().names)
    }

    @Test
    fun onlyTheUsersOwnFoldersOtherThanReadLaterCanBeColored() {
        val inbox = MailFolder("ada login", "M0", "Inbox", role = INBOX_ROLE)
        val readLater = MailFolder("ada login", "M2", READ_LATER_NAME)
        val account = AccountFolders("ada login", "Ada", listOf(inbox, adaClients, readLater), readLaterId = "M2")
        assertTrue(account.colorable(adaClients))
        assertFalse(account.colorable(inbox))
        assertFalse(account.colorable(readLater))
    }
}
