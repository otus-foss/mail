package com.otus.mail.app

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.TRASH_ROLE
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailScope
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderPillsTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = INBOX_ROLE),
                MailFolder("ada", "t", "Trash", role = TRASH_ROLE),
                MailFolder("ada", "l", "Read later"),
                MailFolder("ada", "w", "Work"),
                MailFolder("ada", "c", "Clients", parentId = "w"),
            ),
            readLaterId = "l",
        )
    private val grace =
        AccountFolders(
            "grace",
            "Grace",
            listOf(
                MailFolder("grace", "gi", "Inbox", role = INBOX_ROLE),
                MailFolder("grace", "gt", "Deleted Items", role = TRASH_ROLE),
            ),
        )

    @Test
    fun sharedPillsCoverEveryAccountThenEachAccountListsItsOwnFolders() {
        val options = folderPillOptions(listOf(ada, grace))
        assertEquals(
            listOf(
                InboxPill.ReadLater,
                InboxPill.Role(TRASH_ROLE),
                InboxPill.Folder("ada", "w"),
                InboxPill.Folder("ada", "c"),
            ),
            options.map { it.pill },
            "The inbox is the page itself, and Read later is not listed twice",
        )
        val trash = options[1]
        assertEquals("Trash", trash.label, "Accounts name it differently, so the role names it")
        assertEquals(
            MailScope.Folders(setOf(MailScope.Folder("ada", "t"), MailScope.Folder("grace", "gt"))),
            trash.scope,
        )
        assertEquals(listOf("Folders", "Folders", "Ada", "Ada"), options.map { it.group })
        assertEquals("Work / Clients", options.last().path)
        assertEquals(MailScope.Folder("ada", "c"), options.last().scope)
    }

    @Test
    fun oneSharedNameIsKept() {
        val trash = folderPillOptions(listOf(grace)).single()
        assertEquals("Deleted Items", trash.label)
    }

    @Test
    fun withReadLaterOffItsFolderIsOneOfTheUsersOwn() {
        val options = folderPillOptions(listOf(ada.copy(readLaterId = null)))
        assertEquals(InboxPill.Folder("ada", "l"), options.first { it.label == "Read later" }.pill)
    }
}
