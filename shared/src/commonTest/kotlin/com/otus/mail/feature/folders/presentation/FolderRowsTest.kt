package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
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
                    MailFolder("ada", "i", "Inbox", role = INBOX_ROLE),
                    MailFolder("ada", "rl", "Read later"),
                    MailFolder("ada", "r", "Receipts"),
                ),
                readLaterId = "rl",
            )
        assertEquals(listOf("Receipts"), account.folderRows(emptyList(), allAccounts = false).map { it.folder.name })
    }
}
