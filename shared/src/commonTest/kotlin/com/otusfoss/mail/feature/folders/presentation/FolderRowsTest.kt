package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderRowsTest {
    @Test
    fun settingsListsTheUsersOwnFoldersWithoutSystemFoldersOrReadLater() {
        val account =
            AccountFolders(
                "ada",
                "Ada",
                listOf(
                    MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox),
                    MailFolder("ada", "rl", "Read later"),
                    MailFolder("ada", "r", "Receipts"),
                ),
                readLaterId = "rl",
            )
        assertEquals(listOf("Receipts"), account.folderRows(emptyList(), allAccounts = false).map { it.folder.name })
    }
}
