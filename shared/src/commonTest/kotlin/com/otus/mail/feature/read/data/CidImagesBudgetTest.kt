package com.otus.mail.feature.read.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.domain.MessageHtml
import com.otus.mail.testing.DOWNLOAD_SESSION
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CidImagesBudgetTest {
    private var downloads = 0

    @Test
    fun repeatedLargeImageReferencesAreRejectedBeforeDownloading() =
        runTest {
            val html = "<p>Keep the text</p>" + "<img src='cid:logo'>".repeat(100)
            val result = inline(html, listOf(part("logo", 1_000_000)), ByteArray(1))
            assertEquals(html, result.html?.html)
            assertEquals(0, downloads)
            assertEquals("Keep the text", result.text)
        }

    @Test
    fun dishonestPartSizeCannotBypassTheExpandedHtmlBudget() =
        runTest {
            val html = "<img src='cid:logo'>".repeat(100)
            val result = inline(html, listOf(part("logo", 1)), ByteArray(1_000_000))
            assertEquals(html, result.html?.html)
            assertEquals(1, downloads)
        }

    @Test
    fun manySmallImagesDoNotStartAnUnboundedNumberOfDownloads() =
        runTest {
            val parts = (1..1_000).map { part("image-$it", 1) }
            val html = parts.joinToString("") { "<img src='cid:${it.cid}'>" }
            val result = inline(html, parts, byteArrayOf(1))
            assertTrue(downloads in 1 until parts.size)
            val expanded = assertNotNull(result.html).html
            assertTrue(expanded.contains("data:image/png;base64,AQ=="))
            assertTrue(expanded.contains("cid:image-1000"))
        }

    @Test
    fun actualDownloadSizesConsumeTheSharedBudget() =
        runTest {
            val parts = (1..10).map { part("image-$it", 0) }
            val html = parts.joinToString("") { "<img src='cid:${it.cid}'>" }
            val result = inline(html, parts, ByteArray(3_000_000))
            assertTrue(downloads in 1 until parts.size)
            val expanded = assertNotNull(result.html).html
            assertTrue(expanded.length <= 12 * 1024 * 1024)
            assertTrue(expanded.contains("cid:image-10"))
        }

    @Test
    fun normalRepeatedImagesStillResolveAndShareOneDownload() =
        runTest {
            val html = "<img src='cid:logo'><img src='cid:LOGO'>"
            val result = inline(html, listOf(part("logo", 3)), byteArrayOf(1, 2, 3))
            assertEquals(
                "<img src='data:image/png;base64,AQID'><img src='data:image/png;base64,AQID'>",
                result.html?.html,
            )
            assertEquals(1, downloads)
        }

    @Test
    fun failedDownloadsLeaveTheBudgetForLaterImages() =
        runTest {
            val parts = listOf(part("broken-1", 1), part("broken-2", 1), part("logo", 1))
            val html = parts.joinToString("") { "<img src='cid:${it.cid}'>" }
            val result = inline(html, parts, byteArrayOf(1))
            assertEquals(
                "<img src='cid:broken-1'><img src='cid:broken-2'><img src='data:image/png;base64,AQ=='>",
                result.html?.html,
            )
            assertEquals(3, downloads)
        }

    private suspend fun inline(
        html: String,
        parts: List<EmailBodyPart>,
        bytes: ByteArray,
    ): MessageContent {
        val engine =
            MockEngine { request ->
                if (request.url.encodedPath == "/session") {
                    respond(DOWNLOAD_SESSION)
                } else {
                    downloads += 1
                    if ("broken" in request.url.encodedPath) {
                        respond("", HttpStatusCode.InternalServerError)
                    } else {
                        respond(bytes)
                    }
                }
            }
        val login = JmapLogin("ada", "https://mail.example.org/session", "ada@example.org", "Ada")
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        return JmapConnections(JmapClient(engine), store)
            .mailAccount(login)
            .withInlineImages(
                MessageContent("Keep the text", false, MessageHtml(html, false)),
                parts,
            )
    }

    private fun part(
        cid: String,
        size: Long,
    ) = EmailBodyPart(blobId = cid, cid = cid, type = "image/png", size = size)
}
