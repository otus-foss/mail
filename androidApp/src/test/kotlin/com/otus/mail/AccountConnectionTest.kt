package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The whole flow against a mock JMAP server shaped like Stalwart: connect, list, read the inbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountConnectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store = InMemoryLoginStore()

    private val server =
        MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val authorized = request.headers[HttpHeaders.Authorization] == "Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA=="
            val body = (request.body as? TextContent)?.text.orEmpty()
            when {
                !authorized -> {
                    respond("", HttpStatusCode.Unauthorized)
                }

                request.url.encodedPath == "/.well-known/jmap" -> {
                    respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/jmap/session"))
                }

                request.url.encodedPath == "/jmap/session" -> {
                    respond(SESSION, HttpStatusCode.OK, json)
                }

                else -> {
                    respond(jmapReply(body, ::answer), HttpStatusCode.OK, json)
                }
            }
        }

    /** One inbox holding one email, m1, alone in thread T1; the server keeps no states. */
    private fun answer(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Mailbox/get" -> {
                listResult("m", listOf(parsed(MAILBOX)))
            }

            "Identity/get" -> {
                listResult("i", parsed(IDENTITIES).jsonArray)
            }

            "Email/query" -> {
                buildJsonObject {
                    put("ids", listOf("m1").toJsonArray())
                    put("total", JsonPrimitive(1))
                }
            }

            "Email/get" -> {
                listResult("e", arguments.found("m1", EMAIL))
            }

            "Thread/get" -> {
                listResult("t", arguments.found("T1", THREAD))
            }

            else -> {
                null
            }
        }

    private fun parsed(json: String) = Json.parseToJsonElement(json)

    /** The object [json] when the call asks for [id], which is all this server holds. */
    private fun JsonObject.found(
        id: String,
        json: String,
    ) = ids().orEmpty().filter { it == id }.map { parsed(json) }

    @Test
    fun connectingAnAccountListsItAndFillsTheInbox() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = MailServices(store) { JmapClient(server) }) }
        }
        compose.onNodeWithText("Connect an account in Settings to see your mail.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Email accounts").performClick()
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
        compose.onNodeWithText("Connect an account").performClick()
        compose.onNodeWithContentDescription("Server").performTextInput("mail.example.org")
        compose.onNodeWithContentDescription("Username").performTextInput("ada@example.org")
        compose.onNodeWithContentDescription("Password").performTextInput("wrong")
        compose.onNodeWithText("Connect").performClick()
        compose.waitForNode(hasText("rejected", substring = true))
        assertEquals("Nothing is saved when sign-in fails", 0, store.logins().size)

        compose.onNodeWithContentDescription("Password").performTextReplacement("secret")
        compose.onNodeWithText("Connect").performClick()
        compose.waitForNode(hasText("ada@other.example"))
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
        // The account's addresses are avatar pills now, so the detail below its name keeps only the server.
        compose.onNodeWithText("mail.example.org").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ada@example.org").assertIsDisplayed()
        compose.onNodeWithText("ada@other.example").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("1 account connected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasContentDescription("Inbox", substring = true) and isSelectable()).performClick()
        compose.waitForNode(hasText("Engine notes"))
    }

    private companion object {
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:submission": {}},
            "accounts": {"d1": {"name": "Ada Lovelace", "accountCapabilities": {"urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:submission": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"},
            "username": "ada@example.org", "apiUrl": "/jmap/"}"""
        const val MAILBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}"""
        const val IDENTITIES = """[{"email": "ada@example.org"}, {"email": "ada@other.example"},
            {"email": "ada@example.org"}]"""
        const val EMAIL = """{"id": "m1", "threadId": "T1", "mailboxIds": {"a": true}, "keywords": {},
            "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
            "subject": "Engine notes", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Weaving patterns"}"""
        const val THREAD = """{"id": "T1", "emailIds": ["m1"]}"""
    }
}
