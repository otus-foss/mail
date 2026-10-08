package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JmapUploadTest {
    private fun session(
        template: String? = "/upload/{accountId}/",
        maxSizeUpload: Long? = null,
    ): JmapSession =
        JmapSession(
            "https://mail.example.org/jmap/session",
            "https://mail.example.org/jmap/",
            "Ada",
            setOf(JmapCapability.CORE),
            mapOf("d 1" to JmapAccount("Ada", true, false, emptySet())),
            emptyMap(),
            "s",
            setOf("https://mail.example.org:443"),
            uploadUrl = template,
            capabilityObjects =
                mapOf(
                    JmapCapability.CORE to
                        JsonObject(listOfNotNull(maxSizeUpload?.let { "maxSizeUpload" to JsonPrimitive(it) }).toMap()),
                ),
        )

    @Test
    fun uploadsTheBlobWithItsTypeToTheExpandedTemplate() =
        runTest {
            val script = "require \"fileinto\";".encodeToByteArray()
            val client =
                JmapClient(
                    MockEngine { request ->
                        assertEquals(ADA.header(), request.headers[HttpHeaders.Authorization])
                        assertEquals("/upload/d%201/", request.url.encodedPath)
                        val body = request.body as OutgoingContent.ByteArrayContent
                        assertEquals("application/sieve", body.contentType.toString())
                        assertContentEquals(script, body.bytes())
                        json("""{"accountId": "d 1", "blobId": "G1", "type": "application/sieve", "size": 19}""")
                    },
                )
            val upload = client.upload(session(), ADA, "d 1", script, "application/sieve")
            assertEquals(JmapUpload("G1", "application/sieve", 19), upload)
        }

    @Test
    fun blobsOverTheServerLimitAreNotSent() =
        runTest {
            val client = JmapClient(MockEngine { error("An oversized blob must not be sent") })
            val error =
                assertFailsWith<JmapException.UploadTooLarge> {
                    client.upload(session(maxSizeUpload = 4), ADA, "d 1", ByteArray(5), "application/sieve")
                }
            assertEquals(4, error.limitBytes)
        }

    @Test
    fun untrustedUploadHostReceivesNoRequestOrCredentials() =
        runTest {
            val client = JmapClient(MockEngine { error("Untrusted host must not receive a request") })
            assertFailsWith<JmapException.RedirectedElsewhere> {
                client.upload(session("https://evil.example/{accountId}"), ADA, "d 1", ByteArray(1), "text/plain")
            }
        }

    @Test
    fun redirectsMissingEndpointsAndBadReplies() =
        runTest {
            val moved =
                JmapClient(
                    MockEngine {
                        respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/elsewhere"))
                    },
                )
            assertFailsWith<JmapException.RequestFailed> {
                moved.upload(session(), ADA, "d 1", ByteArray(1), "text/plain")
            }
            val noEndpoint = JmapClient(MockEngine { error("No endpoint, no request") })
            assertFailsWith<JmapException.NotJmap> {
                noEndpoint.upload(session(template = null), ADA, "d 1", ByteArray(1), "text/plain")
            }
            val garbled = JmapClient(MockEngine { json("""{"type": "text/plain"}""") })
            assertFailsWith<JmapException.NotJmap> { garbled.upload(session(), ADA, "d 1", ByteArray(1), "text/plain") }
        }
}
