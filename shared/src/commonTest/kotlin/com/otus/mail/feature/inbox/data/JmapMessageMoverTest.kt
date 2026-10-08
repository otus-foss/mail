package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JmapMessageMoverTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val message =
        MailMessage(
            "ada/m1",
            MailSender("Grace", "grace@example.org"),
            "Order",
            "",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "a"),
            emailId = "m1",
        )

    private fun mover(reply: (String) -> String): JmapMessageMover {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapMessageMover(JmapConnections(JmapClient(stalwartLike(api = reply)), store), store)
    }

    @Test
    fun theMessageLeavesItsMailboxAndJoinsTheFolder() =
        runTest {
            val sent = mutableListOf<String>()
            val moved =
                mover {
                    sent += it
                    """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
                }.move(message, "r")
            assertTrue(moved)
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/r\":true}}" in sent.single(),
                sent.single(),
            )
        }

    @Test
    fun refusalsAndPointlessMovesReportFailure() =
        runTest {
            val refused =
                mover {
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                       "notUpdated": {"m1": {"type": "forbidden"}}}, "c0"]]}"""
                }
            assertFalse(refused.move(message, "r"))
            val idle = mover { error("Nothing is sent") }
            assertFalse(idle.move(message, "a"), "Moving into the same mailbox does nothing")
            assertFalse(idle.move(message.copy(emailId = ""), "r"), "Supplied messages have no server id")
            assertEquals(false, idle.move(message.copy(location = message.location.copy(accountId = "gone")), "r"))
        }
}
