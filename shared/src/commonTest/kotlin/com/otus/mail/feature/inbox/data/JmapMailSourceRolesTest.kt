package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Inbox with Spam and Trash, as the Inbox settings choose. */
class JmapMailSourceRolesTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")

    private val api = { body: String ->
        if ("Mailbox/get" in body) {
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                {"id": "a", "name": "Inbox", "role": "inbox"},
                {"id": "j", "name": "Junk Mail", "role": "junk"},
                {"id": "t", "name": "Deleted Items", "role": "trash"},
                {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
        } else {
            val id = Regex("\"inMailbox\":\"(\\w)\"").find(body)!!.groupValues[1]
            """{"methodResponses": [["Email/query", {"ids": ["m$id"]}, "c0"],
                ["Email/get", {"state": "e", "list": [{"id": "m$id", "subject": "Hi",
                 "receivedAt": "2026-10-04T09:30:00Z"}]}, "c1"]]}"""
        }
    }

    @Test
    fun rolesLoadEachChosenFolderUnderTheAppsName() =
        runTest {
            val store = InMemoryLoginStore().apply { save(login, "secret") }
            val source =
                JmapMailSource(
                    JmapConnections(JmapClient(stalwartLike(api = api)), store),
                    store,
                    zone = { TimeZone.UTC },
                )
            val snapshot = source.messages(MailScope.Roles(mapOf("inbox" to "Inbox", "junk" to "Spam")))
            assertEquals(
                mapOf("ada/ma" to "Inbox", "ada/mj" to "Spam"),
                snapshot.messages.associate { it.id to it.location.mailbox },
            )
        }
}
