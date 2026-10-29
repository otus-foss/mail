package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.testing.DOWNLOAD_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class JmapMessageBodiesTest {
    private val login = ADA_LOGIN
    private val requests = mutableListOf<String>()

    private fun bodies(
        email: String,
        cache: MailCache = InMemoryMailCache(),
        files: Map<String, ByteArray> = emptyMap(),
    ): JmapMessageBodies {
        val api = { body: String ->
            requests += body
            """{"methodResponses": [["Email/get", {"state": "e", "list": [$email]}, "c0"]]}"""
        }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine = stalwartLike(DOWNLOAD_SESSION, files, api)
        return JmapMessageBodies(JmapConnections(JmapClient(engine), store), store, cache)
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

    @Test
    fun aSecondOpenReadsTheBodyFromTheDeviceWithReceivedTimeForTrimming() =
        runTest {
            val cache = InMemoryMailCache()
            val bodies =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z", "attachments": [],
                        "textBody": [{"partId": "1", "type": "text/plain"}],
                        "bodyValues": {"1": {"value": "Kept text"}}}""",
                    cache,
                )
            bodies.content(opened)
            val again = bodies.content(opened)
            assertEquals("Kept text", again?.text)
            assertEquals(1, requests.size, "The second open sends nothing")
            cache.trim(CacheKind.Body, Instant.parse("2026-10-05T00:00:00Z").toEpochMilliseconds())
            assertTrue(cache.readAll("ada", CacheKind.Body).isEmpty(), "Older bodies can be trimmed")
        }

    @Test
    fun aBodySavedBeforeFilesWereFetchedLoadsAgain() =
        runTest {
            val bodies =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z",
                        "textBody": [{"partId": "1", "type": "text/plain"}],
                        "bodyValues": {"1": {"value": "Old copy"}}}""",
                )
            bodies.content(opened)
            bodies.content(opened)
            assertEquals(2, requests.size, "A copy without an attachments list is not trusted")
            assertTrue("\"attachments\"" in requests.first() && "\"cid\"" in requests.first(), requests.first())
            assertTrue("header:List-Unsubscribe:asURLs" in requests.first(), requests.first())
        }

    @Test
    fun cidImagesAreInlinedAndOnlyRealFilesAreListed() =
        runTest {
            val content =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z",
                        "textBody": [{"partId": "1", "type": "text/html"}],
                        "htmlBody": [{"partId": "1", "type": "text/html"}],
                        "bodyValues": {"1": {"value": "<p>Hi</p><img src=\"cid:logo@x\">"}},
                        "attachments": [
                          {"partId": "2", "blobId": "b-logo", "type": "image/png", "size": 3, "cid": "logo@x"},
                          {"partId": "3", "blobId": "b-pdf", "name": "plan.pdf", "type": "application/pdf", "size": 9}],
                        "header:List-Unsubscribe:asURLs": ["https://lists.example.org/u/1"],
                        "header:List-Unsubscribe-Post:asText": "List-Unsubscribe=One-Click",
                        "header:List-Id:asText": "Plans <plans.example.org>"}""",
                    files = mapOf("b-logo" to byteArrayOf(1, 2, 3)),
                ).content(opened)
            assertNotNull(content)
            assertEquals("""<p>Hi</p><img src="data:image/png;base64,AQID">""", content.html?.html)
            assertEquals(listOf("plan.pdf"), content.attachments.map { it.name })
            assertEquals("https://lists.example.org/u/1", content.unsubscribe?.web)
            assertEquals("Plans", content.listName)
        }

    @Test
    fun aCidImageThatFailsToDownloadKeepsItsAddress() =
        runTest {
            val content =
                bodies(
                    """{"id": "e1", "receivedAt": "2026-10-04T09:30:00Z", "attachments": [],
                        "htmlBody": [{"partId": "1", "type": "text/html"},
                          {"partId": "2", "blobId": "gone", "type": "image/gif", "cid": "pic"}],
                        "bodyValues": {"1": {"value": "<img src='cid:pic'>"}}}""",
                ).content(opened)
            assertEquals("<img src='cid:pic'>", content?.html?.html)
        }
}
