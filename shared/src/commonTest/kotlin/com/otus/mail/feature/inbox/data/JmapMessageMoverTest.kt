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
                }.move(message, "r", leaving = setOf("a"))
            assertTrue(moved)
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/r\":true}}" in sent.single(),
                sent.single(),
            )
        }

    @Test
    fun aMoveCanLeaveSeveralFoldersAndOnlyNamedOnesChange() =
        runTest {
            val sent = mutableListOf<String>()
            val updated = """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
            val mover =
                mover {
                    sent += it
                    updated
                }
            assertTrue(mover.move(message, "t", leaving = setOf("a", "l")))
            assertTrue(mover.setInFolder(message, "l", inFolder = true))
            assertTrue(mover.setInFolder(message, "l", inFolder = false))
            assertTrue(
                "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/l\":null,\"mailboxIds/t\":true}}" in sent[0],
                sent[0],
            )
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/l\":true}}" in sent[1], sent[1])
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/l\":null}}" in sent[2], sent[2])
        }

    @Test
    fun refusalsAndPointlessMovesReportFailure() =
        runTest {
            val refused =
                mover {
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                       "notUpdated": {"m1": {"type": "forbidden"}}}, "c0"]]}"""
                }
            assertFalse(refused.move(message, "r", setOf("a")))
            val idle = mover { error("Nothing is sent") }
            assertFalse(idle.move(message, "a", setOf("a")), "Moving into the same mailbox does nothing")
            assertFalse(idle.move(message.copy(emailId = ""), "r", setOf("a")), "Supplied messages have no server id")
            val gone = message.copy(location = message.location.copy(accountId = "gone"))
            assertEquals(false, idle.move(gone, "r", setOf("a")))
        }
}
