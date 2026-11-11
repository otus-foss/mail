package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapSenderMailsTest {
    private val requests = mutableListOf<String>()

    private val open =
        MailMessage(
            "ada/g9",
            MailSender("GitHub", SENDER),
            "You have used 100% of the storage",
            "",
            ReceivedAt("2026-11-10", "20:56"),
            MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "g9",
        )

    private fun senderMails(reply: String): JmapSenderMails {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike { body ->
                requests += body
                reply
            }
        return JmapSenderMails(JmapConnections(JmapClient(engine), store), Mailboxes(InMemoryMailCache()))
    }

    @Test
    fun aServerWhoseSearchFindsNothingStillListsTheSendersRecentMail() =
        runTest {
            val recent =
                listOf(
                    email("g9", "10T20:56"),
                    email("g8", "10T09:00"),
                    email("x1", "09T12:00", from = OTHER),
                    email("g7", "06T08:00"),
                )
            val mails = senderMails(reply(found = emptyList(), recent = recent)).newest(open, setOf("g9"))
            assertEquals(
                listOf("g8", "g7"),
                mails.map { it.emailId },
                "The server's search missed mail the account lists",
            )
        }

    @Test
    fun searchedAndRecentMailMergeNewestFirstWithEachMailOnce() =
        runTest {
            val found = listOf(email("g8", "10T09:00"), email("g2", "01T08:00"))
            val recent = listOf(email("g9", "10T20:56"), email("g8", "10T09:00"), email("g7", "06T08:00", seen = true))
            val mails = senderMails(reply(found, recent)).newest(open, setOf("g9"))
            assertEquals(listOf("g8", "g7", "g2"), mails.map { it.emailId })
            assertEquals(listOf(true, false, true), mails.map { it.unread })
            assertEquals("Subject g8", mails.first().subject)
            assertEquals(ReceivedAt("2026-11-10", "09:00"), mails.first().received.copy(time = "09:00"))
        }

    @Test
    fun mailInTheTrashOrOnlyNamedAfterTheSenderDoesNotCount() =
        runTest {
            val found = listOf(email("t1", "09T10:00", box = "t"), email("n1", "08T10:00", from = OTHER))
            val recent = listOf(email("t2", "10T10:00", box = "t"), email("g7", "06T08:00"))
            val mails = senderMails(reply(found, recent)).newest(open, setOf("g9"))
            assertEquals(listOf("g7"), mails.map { it.emailId })
        }

    @Test
    fun aSenderWithMuchMailListsOnlyTheNewestThree() =
        runTest {
            val recent = (1..6).map { email("g$it", "0${it}T08:00") }
            val mails = senderMails(reply(found = emptyList(), recent = recent)).newest(open, setOf("g9"))
            assertEquals(listOf("g6", "g5", "g4"), mails.map { it.emailId })
        }

    @Test
    fun aSenderWithNoOtherMailGivesNoneInOneRequest() =
        runTest {
            val recent = listOf(email("g9", "10T20:56"), email("x1", "09T12:00", from = OTHER))
            val mails =
                senderMails(
                    reply(found = listOf(email("g9", "10T20:56")), recent = recent),
                ).newest(open, setOf("g9"))
            assertTrue(mails.isEmpty(), "$mails")
            assertEquals(1, requests.size, "Both lookups travel in one request")
            assertTrue("\"from\":\"$SENDER\"" in requests.single(), requests.single())
        }

    private companion object {
        const val SENDER = "noreply@github.example"
        const val OTHER = "news@shop.example"

        /** An email of November 2026, [at] its day and time, such as `10T09:00`. */
        fun email(
            id: String,
            at: String,
            box: String = "a",
            from: String = SENDER,
            seen: Boolean = false,
        ): String =
            """{"id": "$id", "threadId": "t-$id", "receivedAt": "2026-11-$at:00Z", "mailboxIds": {"$box": true}, """ +
                """"keywords": {${if (seen) "\"\$seen\": true" else ""}}, """ +
                """"subject": "Subject $id", "preview": "Preview $id", """ +
                """"from": [{"name": "GitHub", "email": "$from"}]}"""

        /** The server's answer: its folders, what its search by sender [found], and its [recent] mail. */
        fun reply(
            found: List<String>,
            recent: List<String>,
        ): String =
            """{"methodResponses": [
            ["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"}, {"id": "t", "role": "trash"}]}, "c0"],
            ["Email/query", {"queryState": "q", "ids": []}, "c1"],
            ["Email/get", {"state": "e", "list": [${found.joinToString()}]}, "c2"],
            ["Email/query", {"queryState": "q", "ids": []}, "c3"],
            ["Email/get", {"state": "e", "list": [${recent.joinToString()}]}, "c4"]]}"""
    }
}
