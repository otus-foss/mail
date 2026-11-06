package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.testing.ADA_LOGIN
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Inbox with Spam and Trash, as the Inbox settings choose. */
class JmapMailSourceRolesTest {
    private val login = ADA_LOGIN

    private val server =
        FakeMailServer(
            """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "j", "name": "Junk Mail", "role": "junk"},
            {"id": "t", "name": "Deleted Items", "role": "trash"}, {"id": "r", "name": "Receipts"}""",
            listOf("a", "j", "t", "r").joinToString {
                """{"id": "m$it", "subject": "Hi", "receivedAt": "2026-10-04T09:30:00Z", "mailboxIds": {"$it": true}}"""
            },
        )

    @Test
    fun rolesLoadEachChosenFolderUnderTheAppsName() =
        runTest {
            val store = InMemoryLoginStore().apply { save(login, "secret") }
            val snapshot = server.source(store).messages(MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Junk)))
            assertEquals(
                mapOf("ada/ma" to "Inbox", "ada/mj" to "Spam"),
                snapshot.messages.associate { it.id to it.location.mailbox },
            )
        }
}
