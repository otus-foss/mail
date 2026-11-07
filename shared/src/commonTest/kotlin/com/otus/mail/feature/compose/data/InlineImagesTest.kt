package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.ADA_SECRET_HEADER
import com.otus.mail.testing.composeMail
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Signature and body images in a saved draft: only `<img>` sources become parts of the message. */
class InlineImagesTest {
    private val uploads = mutableListOf<String>()
    private val created = mutableListOf<String>()

    private fun repository(): DraftRepository {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                check(request.headers[HttpHeaders.Authorization] == ADA_SECRET_HEADER)
                when (request.url.encodedPath) {
                    "/.well-known/jmap" -> {
                        respond(SESSION, HttpStatusCode.OK, json)
                    }

                    "/upload/d1/" -> {
                        uploads += request.body.contentType.toString()
                        respond("""{"blobId": "B${uploads.size}", "type": "", "size": 3}""", HttpStatusCode.OK, json)
                    }

                    else -> {
                        val body = (request.body as TextContent).text
                        respond(answer(body), HttpStatusCode.OK, json)
                    }
                }
            }
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        return composeMail(JmapConnections(JmapClient(engine), store)).drafts
    }

    private fun answer(body: String): String =
        when {
            "Mailbox/get" in body -> {
                """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [{"id": "dr", "role": "drafts"}]}, "c0"]]}"""
            }

            "\"create\"" in body -> {
                created += body
                """{"methodResponses": [["Email/set", {"newState": "e2", "created": {"draft": {"id": "e9"}}}, "c0"]]}"""
            }

            else -> {
                error("Unexpected call: $body")
            }
        }

    @Test
    fun textThatLooksLikeADataUriIsSavedAsWritten() =
        runTest {
            val text = "<p>data:image/png;base64,iVBOR and data:x;base64,abc</p>"
            val saved = repository().save(Draft("ada", body = "text", html = text))
            assertEquals("e9", saved.getOrThrow())
            assertTrue(uploads.isEmpty(), "Nothing in the text is uploaded")
            assertTrue(text in createdHtml(), createdHtml())
        }

    @Test
    fun anImageWhoseDataDoesNotDecodeIsLeftAsItIs() =
        runTest {
            val broken = """<img src="data:image/png;base64,AA==AA" alt="">"""
            val saved = repository().save(Draft("ada", body = "Hi", signature = Signature(html = broken)))
            assertEquals("e9", saved.getOrThrow())
            assertTrue(uploads.isEmpty())
            assertTrue(broken in createdHtml(), createdHtml())
        }

    @Test
    fun onlyTheImageSourceBecomesAPartAndTheSameTextElsewhereStays() =
        runTest {
            val uri = "data:image/png;base64,iVBORw0K\n Ggo="
            val signature = """<div><img width="96" src="$uri"></div>"""
            val draft = Draft("ada", body = "See $uri", signature = Signature(html = signature))
            repository().save(draft).getOrThrow()
            assertEquals(listOf("image/png"), uploads)
            val html = createdHtml()
            assertTrue("""<img width="96" src="cid:image0@otus.mail">""" in html, html)
            assertTrue("See data:image/png;base64,iVBORw0K" in html, "The body's text is not an image")
        }

    @Test
    fun aSignatureImageUploadsOnceForEverySaveThatShowsIt() =
        runTest {
            val signature = Signature(html = """<img src="data:image/png;base64,iVBORw0KGgo=" width="96">""")
            val drafts = repository()
            drafts.save(Draft("ada", body = "Hi", signature = signature)).getOrThrow()
            drafts.save(Draft("ada", body = "Hi there", signature = signature)).getOrThrow()
            assertEquals(1, uploads.size, "The second save reuses the upload")
            assertTrue("cid:image0@otus.mail" in createdHtml())
            val parts = created.last().substringAfter("\"multipart/related\"")
            assertTrue("\"blobId\":\"B1\"" in parts, parts)
        }

    private fun createdHtml(): String =
        Json
            .parseToJsonElement(created.last())
            .jsonObject
            .getValue("methodCalls")
            .jsonArray
            .single()
            .jsonArray[1]
            .jsonObject
            .getValue("create")
            .jsonObject
            .getValue("draft")
            .jsonObject
            .getValue("bodyValues")
            .jsonObject
            .getValue("html")
            .jsonObject
            .getValue("value")
            .jsonPrimitive.content

    private companion object {
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org",
            "apiUrl": "/jmap/", "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/",
            "state": "s1"}"""
    }
}
