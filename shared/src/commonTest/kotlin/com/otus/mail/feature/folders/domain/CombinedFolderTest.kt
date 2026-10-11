package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedFolderTest {
    private fun account(
        id: String,
        vararg folders: MailFolder,
    ) = AccountFolders(id, id, folders.toList())

    @Test
    fun eachRoleSomeAccountHasIsListedOnceWithTheUnreadMailOfAll() {
        val ada =
            account(
                "ada",
                MailFolder("ada", "t", "Deleted Items", role = TRASH_ROLE, unread = 2),
                MailFolder("ada", "j", "Junk Mail", role = JUNK_ROLE, unread = 4),
                MailFolder("ada", "i", "Inbox", role = INBOX_ROLE, unread = 9),
            )
        val grace =
            account(
                "grace",
                MailFolder("grace", "t", "Trash", role = TRASH_ROLE, unread = 1),
                MailFolder("grace", "s", "Sent", role = SENT_ROLE),
                MailFolder("grace", "w", "Work", unread = 5),
            )
        assertEquals(
            mapOf(CombinedFolder.Sent to 0L, CombinedFolder.Spam to 4L, CombinedFolder.Trash to 3L),
            combinedFolders(listOf(ada, grace)),
        )
        assertEquals(
            listOf(CombinedFolder.Sent, CombinedFolder.Spam, CombinedFolder.Trash),
            combinedFolders(listOf(grace, ada)).keys.toList(),
            "Sidebar order, whatever the order of accounts",
        )
    }
}
