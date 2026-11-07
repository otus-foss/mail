package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.CONTACTS_ONLY_SESSION
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapSenderHistoryTest {
    private val login = ADA_LOGIN
    private val message =
        MailMessage(
            "ada/m1",
            MailSender("Billing", "billing@acme.example"),
            "Invoice",
            "",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "m1",
        )

    private fun history(
        session: String = STALWART_SESSION,
        reply: (String) -> String,
    ): JmapSenderHistory {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapSenderHistory(JmapConnections(JmapClient(stalwartLike(session, reply)), store))
    }

    @Test
    fun theSendersNewestMailIsQueriedAndOnlyItsFoldersAreRead() =
        runTest {
            val sent = mutableListOf<String>()
            val folders =
                history {
                    sent += it
                    """{"methodResponses": [["Email/query", {"ids": ["m1", "m2"]}, "c0"],
                    ["Email/get", {"state": "e", "list": [{"id": "m1", "mailboxIds": {"a": true}},
                    {"id": "m2", "mailboxIds": {"r": true, "x": false}}]}, "c1"]]}"""
                }.recentFolders(message)
            assertEquals(listOf(setOf("a"), setOf("r")), folders)
            val request = sent.single()
            assertTrue("\"filter\":{\"from\":\"billing@acme.example\"}" in request, request)
            assertTrue("\"limit\":50" in request, request)
            assertTrue("\"properties\":[\"id\",\"mailboxIds\"]" in request, request)
        }

    @Test
    fun aFailedQueryOrASuppliedMessageGivesNoHistory() =
        runTest {
            val failing = history { error("The server is down") }
            assertEquals(emptyList(), history { "not json" }.recentFolders(message))
            assertEquals(emptyList(), failing.recentFolders(message.copy(emailId = "")))
        }

    @Test
    fun aLoginWithoutMailHasNoHistory() =
        runTest {
            val contactsOnly = history(CONTACTS_ONLY_SESSION) { error("Nothing is sent") }
            assertEquals(emptyList(), contactsOnly.recentFolders(message))
        }
}
