package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

                "Email/query" in body -> {
                    respond(EMAILS, HttpStatusCode.OK, json)
                }

                else -> {
                    respond(MAILBOXES, HttpStatusCode.OK, json)
                }
            }
        }

    @Test
    fun connectingAnAccountListsItAndFillsTheInbox() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = MailServices(store) { JmapClient(server) }) }
        }
        compose.onNodeWithText("Connect an account in Settings to see your mail.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Settings") and isSelectable()).performClick()
        compose.onNodeWithText("Email accounts").performClick()
        compose.onNodeWithContentDescription("Server").performTextInput("mail.example.org")
        compose.onNodeWithContentDescription("Username").performTextInput("ada@example.org")
        compose.onNodeWithContentDescription("Password").performTextInput("wrong")
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("rejected", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Nothing is saved when sign-in fails", 0, store.logins().size)

        compose.onNodeWithContentDescription("Password").performTextReplacement("secret")
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasText("Connected Ada Lovelace.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ada@example.org · mail.example.org").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("1 account connected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isSelectable()).performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Engine notes")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 message").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
            "accounts": {"d1": {"name": "Ada Lovelace", "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"},
            "username": "ada@example.org", "apiUrl": "/jmap/"}"""
        const val MAILBOXES = """{"methodResponses": [["Mailbox/get",
            {"state": "m", "list": [{"id": "a", "name": "Inbox", "role": "inbox"}]},
            "c0"]]}"""
        const val EMAILS = """{"methodResponses": [["Email/query", {"ids": ["m1"]}, "c0"],
            ["Email/get", {"state": "e", "list": [
            {"id": "m1", "keywords": {}, "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
             "subject": "Engine notes", "receivedAt": "2026-10-04T09:30:00Z",
             "preview": "Weaving patterns"}]}, "c1"]]}"""
    }
}
