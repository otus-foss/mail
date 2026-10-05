package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmapDownloadTest {
    private val pngHeaders = headersOf(HttpHeaders.ContentType, "image/png")

    private fun session(template: String): JmapSession =
        JmapSession(
            "https://mail.example.org/jmap/session",
            "https://mail.example.org/jmap/",
            "Ada",
            setOf(JmapCapability.CORE),
            mapOf("contacts" to JmapAccount("Ada", true, false, emptySet())),
            emptyMap(),
            "s",
            setOf("https://mail.example.org:443"),
            template,
        )

    @Test
    fun templateValuesArePercentEncodedAndAuthenticated() =
        runTest {
            val bytes = byteArrayOf(1, 2, 3)
            val client =
                JmapClient(
                    MockEngine { request ->
                        assertEquals(ADA.header(), request.headers[HttpHeaders.Authorization])
                        assertTrue("photo%2F%3F%23" in request.url.encodedPath)
                        assertEquals("image/png", request.url.parameters["type"])
                        respond(bytes, headers = pngHeaders)
                    },
                )
            val blob =
                client.download(
                    session("/download/{accountId}/{blobId}/{name}?type={type}"),
                    ADA,
                    "contacts",
                    "photo/?#",
                    "image/png",
                    10,
                )
            assertContentEquals(bytes, blob.bytes)
        }

    @Test
    fun untrustedDownloadHostReceivesNoRequestOrCredentials() =
        runTest {
            val client = JmapClient(MockEngine { error("Untrusted host must not receive a request") })
            assertFailsWith<JmapException.RedirectedElsewhere> {
                val target = session("https://evil.example/{blobId}")
                client.download(target, ADA, "contacts", "photo", "image/png", 10)
            }
        }

    @Test
    fun blobRedirectIsRefusedWithoutFollowingIt() =
        runTest {
            var calls = 0
            val client =
                JmapClient(
                    MockEngine {
                        calls++
                        val redirect = headersOf(HttpHeaders.Location, "https://evil.example/avatar")
                        respond("", HttpStatusCode.Found, redirect)
                    },
                )
            assertFailsWith<JmapException.RequestFailed> {
                client.download(session("/download/{blobId}"), ADA, "contacts", "photo", "image/png", 10)
            }
            assertEquals(1, calls)
        }

    @Test
    fun blobSizeLimitWorksWithoutContentLength() =
        runTest {
            val engine = MockEngine { respond(ByteArray(11), headers = pngHeaders) }
            val client = JmapClient(engine)
            assertFailsWith<JmapException.ResponseTooLarge> {
                client.download(session("/download/{blobId}"), ADA, "contacts", "photo", "image/png", 10)
            }
        }
}
