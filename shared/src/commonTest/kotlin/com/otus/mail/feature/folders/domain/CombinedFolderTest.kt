package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.JUNK_ROLE
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.core.mail.TRASH_ROLE
import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedFolderTest {
    private fun account(
        id: String,
        vararg folders: MailFolder,
    ) = AccountFolders(id, id, folders.toList())

    @Test
    fun eachRoleSomeAccountHasIsListedOnceWithEveryAccountsFolder() {
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
            mapOf(
                CombinedFolder.Sent to listOf("grace/s"),
                CombinedFolder.Spam to listOf("ada/j"),
                CombinedFolder.Trash to listOf("ada/t", "grace/t"),
            ),
            combinedFolders(listOf(ada, grace)).mapValues { (_, folders) ->
                folders.map { "${it.accountId}/${it.id}" }
            },
        )
        assertEquals(
            listOf(CombinedFolder.Sent, CombinedFolder.Spam, CombinedFolder.Trash),
            combinedFolders(listOf(grace, ada)).keys.toList(),
            "Sidebar order, whatever the order of accounts",
        )
    }
}
