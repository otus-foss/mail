package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.JmapEmailWriter
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.ThreadMember
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailMarksTest {
    private val login = ADA_LOGIN

    /** A second login on another server, with the same credentials so the mock server accepts it. */
    private val work = JmapLogin("work", "https://work.example.org/.well-known/jmap", "ada@example.org", "Work")
    private val message = message("m1")
    private val updated = """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""

    /** A session that takes two objects per `/set` call. */
    private val smallBatches =
        STALWART_SESSION.replace(
            "\"urn:ietf:params:jmap:core\": {}",
            "\"urn:ietf:params:jmap:core\": {\"maxObjectsInSet\": 2}",
        )

    private fun message(
        id: String,
        loginId: String = "ada",
    ) = MailMessage(
        "$loginId/$id",
        MailSender("Grace", "grace@example.org"),
        "Order",
        "",
        ReceivedAt("2026-10-04", "09:30"),
        MailLocation("Inbox", "Ada", loginId, mailboxId = "a", mailboxRole = MailboxRole.Inbox),
        emailId = id,
    )

    private fun marker(
        session: String = STALWART_SESSION,
        reply: (String) -> String,
    ): JmapEmailWriter {
        val store =
            InMemoryLoginStore().apply {
                save(login, "secret")
                save(work, "secret")
            }
        return emailWriter(JmapConnections(JmapClient(stalwartLike(session, reply)), store))
    }

    private suspend fun sentFor(
        flag: MessageFlag,
        on: Boolean,
    ): String {
        val sent = mutableListOf<String>()
        val marked =
            marker {
                sent += it
                updated
            }.mark(listOf(message), flag, on)
        assertEquals(setOf("ada/m1"), marked)
        return sent.single()
    }

    /** Updates every id a `/set` call names, except [refused] ones. */
    private fun setReply(
        body: String,
        refused: Set<String> = emptySet(),
    ): String {
        val (kept, changed) = updateIds(body).partition { it in refused }
        val updatedIds = changed.joinToString { "\"$it\": null" }
        val refusedIds = kept.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        return """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {$updatedIds},
            "notUpdated": {$refusedIds}}, "c0"]]}"""
    }

    private fun updateIds(body: String): List<String> = UPDATE.findAll(body).map { it.groupValues[1] }.toList()

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
            assertEquals(emptySet(), refused.mark(listOf(message), MessageFlag.Flagged, true))
            val idle = marker { error("Nothing is sent") }
            val supplied = message.copy(emailId = "")
            assertEquals(
                emptySet(),
                idle.mark(listOf(supplied), MessageFlag.Read, true),
                "Supplied messages have no server id",
            )
            val gone = message.copy(location = message.location.copy(accountId = "gone"))
            assertEquals(emptySet(), idle.mark(listOf(gone), MessageFlag.Read, true), "A removed login sends nothing")
        }

    @Test
    fun manyMessagesTakeOneCallPerAccountWithinMaxObjectsInSet() =
        runTest {
            val sent = mutableListOf<String>()
            val messages = listOf(message("m1"), message("w1", "work"), message("m2"), message("m3"))
            val marked =
                marker(smallBatches) {
                    sent += it
                    setReply(it)
                }.mark(messages, MessageFlag.Read, true)
            assertEquals(messages.map { it.id }.toSet(), marked)
            // Accounts are asked at the same time, so only each account's own batches keep their order.
            val batches = sent.map(::updateIds)
            assertEquals(listOf(listOf("m1", "m2"), listOf("m3")), batches.filter { it.first().startsWith("m") })
            assertEquals(listOf(listOf("w1")), batches.filter { it.first().startsWith("w") })
            assertTrue(sent.all { CALL.findAll(it).count() == 1 }, "One Email/set call per request")
        }

    @Test
    fun refusedMessagesAreLeftOutAndFailedBatchesWait() =
        runTest {
            val messages = listOf(message("m1"), message("m2"), message("m3"), message("w1", "work"))
            val refused = marker(smallBatches) { setReply(it, refused = setOf("m2")) }
            assertEquals(setOf("ada/m1", "ada/m3", "work/w1"), refused.mark(messages, MessageFlag.Read, true))
            // The second batch of the first account fails.
            val failing = marker(smallBatches) { if ("\"m3\"" in it) "Service unavailable" else setReply(it) }
            assertEquals(
                messages.map { it.id }.toSet(),
                failing.mark(messages, MessageFlag.Read, true),
                "A batch the server did not get waits for it and counts as made",
            )
            val gone = message("g1", "gone")
            assertEquals(
                setOf("ada/m1"),
                marker { setReply(it) }.mark(listOf(gone, message("m1")), MessageFlag.Read, true),
            )
        }

    @Test
    fun aMarkAppliesToEveryEmailOfAConversationInTheMailbox() =
        runTest {
            val sent = mutableListOf<String>()
            val members = listOf("m1", "m2", "m3").map { ThreadMember(it, setOf("a"), "a") }
            val thread = message("m1").copy(conversation = conversationOf(members))
            val marked =
                marker(smallBatches) {
                    sent += it
                    setReply(it, refused = setOf("m2"))
                }.mark(listOf(thread, message("m4")), MessageFlag.Read, true)
            assertEquals(setOf("ada/m4"), marked, "A conversation succeeds only when every member changed")
            assertEquals(listOf(listOf("m1", "m2"), listOf("m3", "m4")), sent.map(::updateIds))
        }

    @Test
    fun aRefusedRepresentativeAlsoLeavesTheConversationIncomplete() =
        runTest {
            val members = listOf("m1", "m2", "m3").map { ThreadMember(it, setOf("a"), "a") }
            val row = message("m1").copy(conversation = conversationOf(members))
            val changed =
                marker(smallBatches) { setReply(it, refused = setOf("m1")) }
                    .mark(listOf(row), MessageFlag.Read, true)
            assertTrue(changed.isEmpty())
        }

    private companion object {
        val UPDATE = Regex("\"([^\"]+)\":\\{\"keywords/")
        val CALL = Regex("\\[\"Email/set\"")
    }
}
