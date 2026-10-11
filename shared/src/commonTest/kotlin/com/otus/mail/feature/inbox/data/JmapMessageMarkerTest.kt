package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JmapMessageMarkerTest {
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
    private val updated = """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""

    private fun marker(reply: (String) -> String): JmapMessageMarker {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapMessageMarker(JmapConnections(JmapClient(stalwartLike(api = reply)), store), store)
    }

    private suspend fun sentFor(
        flag: MessageFlag,
        on: Boolean,
    ): String {
        val sent = mutableListOf<String>()
        assertTrue(
            marker {
                sent += it
                updated
            }.mark(message, flag, on),
        )
        return sent.single()
    }

    @Test
    fun eachMarkPatchesOnlyItsOwnKeyword() =
        runTest {
            val read = sentFor(MessageFlag.Read, on = true)
            assertTrue("\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in read, read)
            val flag = sentFor(MessageFlag.Flagged, on = true)
            assertTrue("\"update\":{\"m1\":{\"keywords/\$flagged\":true}}" in flag, flag)
        }

    @Test
    fun turningAMarkOffRemovesTheKeyword() =
        runTest {
            val unread = sentFor(MessageFlag.Read, on = false)
            assertTrue("\"update\":{\"m1\":{\"keywords/\$seen\":null}}" in unread, unread)
        }

    @Test
    fun refusalsAndUnknownMessagesReportFailure() =
        runTest {
            val refused =
                marker {
                    """{"methodResponses": [["Email/set", {"newState": "e2",
                       "notUpdated": {"m1": {"type": "forbidden"}}}, "c0"]]}"""
                }
            assertFalse(refused.mark(message, MessageFlag.Flagged, true))
            val idle = marker { error("Nothing is sent") }
            val supplied = message.copy(emailId = "")
            assertFalse(idle.mark(supplied, MessageFlag.Read, true), "Supplied messages have no server id")
            val gone = message.copy(location = message.location.copy(accountId = "gone"))
            assertFalse(idle.mark(gone, MessageFlag.Read, true), "A removed login sends nothing")
        }
}
