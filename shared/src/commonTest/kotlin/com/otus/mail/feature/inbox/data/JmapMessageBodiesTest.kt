package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapMessageBodiesTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val requests = mutableListOf<String>()

    private fun bodies(email: String): JmapMessageBodies {
        val api = { body: String ->
            requests += body
            """{"methodResponses": [["Email/get", {"state": "e", "list": [$email]}, "c0"]]}"""
        }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapMessageBodies(JmapConnections(JmapClient(stalwartLike(api = api)), store), store)
    }

    private val opened =
        MailMessage(
            "ada/e1",
            MailSender("Grace", "grace@example.org"),
            "Plans",
            "Short preview",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", "ada"),
            emailId = "e1",
            preview = "Short preview",
        )

    @Test
    fun aPlainMessageLoadsItsWholeTextAndNoHtml() =
        runTest {
            val content =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z",
                        "textBody": [{"partId": "1", "type": "text/plain"}],
                        "htmlBody": [{"partId": "1", "type": "text/plain"}],
                        "bodyValues": {"1": {"value": "Line one\nLine two, after the preview"}}}""",
                ).content(opened)
            assertNotNull(content)
            val text = "Line one\nLine two, after the preview"
            assertEquals(text, content.text)
            assertNull(content.html)
            assertEquals(text, opened.withContent(content).body, "Replies quote all of it")
            val request = requests.single()
            assertTrue("\"fetchAllBodyValues\":true" in request, request)
            assertTrue("\"textBody\"" in request && "\"htmlBody\"" in request, request)
        }

    @Test
    fun anHtmlMessageLoadsBothItsHtmlAndItsTextAndACutIsMarked() =
        runTest {
            val content =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z",
                        "textBody": [{"partId": "1", "type": "text/html"}],
                        "htmlBody": [{"partId": "1", "type": "text/html"}],
                        "bodyValues": {"1": {"value": "<p>Hello &amp; welcome</p>", "isTruncated": true}}}""",
                ).content(opened)
            assertNotNull(content)
            assertEquals("Hello & welcome", content.text, "An HTML-only message reads as text for replies")
            assertEquals("<p>Hello &amp; welcome</p>", content.html?.html)
            assertTrue(content.truncated && content.html?.truncated == true)
        }

    @Test
    fun anUnknownLoginLoadsNothing() =
        runTest {
            val stranger = opened.copy(location = opened.location.copy(accountId = "gone"))
            assertNull(bodies("").content(stranger))
            assertTrue(requests.isEmpty())
        }
}
