package com.otus.mail.core.mailbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailboxRoleTest {
    private fun account(
        id: String,
        vararg folders: MailFolder,
    ) = AccountFolders(id, id, folders.toList())

    @Test
    fun eachRoleSomeAccountHasIsListedOnceWithEveryAccountsFolder() {
        val ada =
            account(
                "ada",
                MailFolder("ada", "t", "Deleted Items", role = MailboxRole.Trash, unread = 2),
                MailFolder("ada", "j", "Junk Mail", role = MailboxRole.Junk, unread = 4),
                MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox, unread = 9),
            )
        val grace =
            account(
                "grace",
                MailFolder("grace", "t", "Trash", role = MailboxRole.Trash, unread = 1),
                MailFolder("grace", "s", "Sent", role = MailboxRole.Sent),
                MailFolder("grace", "w", "Work", unread = 5),
            )
        assertEquals(
            mapOf(
                MailboxRole.Sent to listOf("grace/s"),
                MailboxRole.Junk to listOf("ada/j"),
                MailboxRole.Trash to listOf("ada/t", "grace/t"),
            ),
            combinedFolders(listOf(ada, grace)).mapValues { (_, folders) ->
                folders.map { "${it.accountId}/${it.id}" }
            },
        )
        assertEquals(
            listOf(MailboxRole.Sent, MailboxRole.Junk, MailboxRole.Trash),
            combinedFolders(listOf(grace, ada)).keys.toList(),
            "Sidebar order, whatever the order of accounts",
        )
    }

    @Test
    fun rolesReadInAnyCaseAndOthersHaveNoPlace() {
        assertEquals(MailboxRole.Inbox, MailboxRole.of("Inbox"))
        assertEquals(MailboxRole.Junk, MailboxRole.of("junk"))
        assertNull(MailboxRole.of("important"))
        assertNull(MailboxRole.of(null))
    }
}
