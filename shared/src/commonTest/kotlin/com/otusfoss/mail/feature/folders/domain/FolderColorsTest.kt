package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.sharedFolders
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderColorsTest {
    private fun store(saved: Map<String, String> = emptyMap()) = DeviceStore(MemoryMapStore(saved), FolderColorStore)

    private val adaClients = MailFolder("ada login", "M1", "Clients")
    private val bobClients = MailFolder("bob login", "M1", "Clients")

    private fun shared(vararg accounts: AccountFolders) = sharedFolders(accounts.toList()).single()

    @Test
    fun colorsSurviveTheStoreKeyedByAccountAndMailbox() {
        val store = store()
        val colors = FolderColors().with(listOf(adaClients), "Red").with(listOf(bobClients), "Blue")
        store.write(colors.names)
        assertEquals(colors, FolderColors(store.read()))
        assertEquals("Red", FolderColors(store.read()).of(adaClients))
        assertEquals("Blue", FolderColors(store.read()).of(bobClients))
    }

    @Test
    fun unreadableEntriesReadAsTheDefault() {
        val store = store(mapOf("M1" to "Red", ":ada" to "Red", "M1:ada login" to " ", "M2:ada login" to "Teal"))
        assertEquals(mapOf(FolderKey("ada login", "M2") to "Teal"), store.read())
        assertNull(FolderColors(store.read()).of(adaClients))
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
    fun onlyTheUsersOwnFoldersOtherThanReadLaterCanBeColored() {
        val inbox = MailFolder("ada login", "M0", "Inbox", role = MailboxRole.Inbox)
        val readLater = MailFolder("ada login", "M2", READ_LATER_NAME)
        val account = AccountFolders("ada login", "Ada", listOf(inbox, adaClients, readLater), readLaterId = "M2")
        assertTrue(account.colorable(adaClients))
        assertFalse(account.colorable(inbox))
        assertFalse(account.colorable(readLater))
    }
}
