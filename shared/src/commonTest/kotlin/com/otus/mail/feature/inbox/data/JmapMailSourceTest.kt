package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapMailSourceTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")

    private fun api(body: String): String =
        if ("Mailbox/get" in body) {
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                {"id": "s", "name": "Sent Items", "role": "sent"},
                {"id": "a", "name": "Posteingang", "role": "inbox"}]}, "c0"]]}"""
        } else {
            check(
                "\"inMailbox\":\"a\"" in body && "\"#ids\"" in body,
            ) { "Queries the inbox by role, chained by back-reference" }
            """{"methodResponses": [
                ["Email/query", {"ids": ["m1", "m2"]}, "c0"],
                ["Email/get", {"state": "e", "list": [
                  {"id": "m1", "keywords": {}, "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
                   "subject": "Compiler draft", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Attached…",
                   "hasAttachment": true, "attachments": [{"partId": "2", "name": "draft.pdf"}],
                   "textBody": [{"partId": "1"}], "bodyValues": {"1": {"value": "Attached is the first draft."}}},
                  {"id": "m2", "keywords": {"${'$'}seen": true}, "from": [{"email": "ada@example.org"}],
                   "subject": null, "receivedAt": "2026-10-03T23:05:00Z", "preview": "Notes", "hasAttachment": false}
                ]}, "c1"]]}"""
        }

    private fun source(store: InMemoryLoginStore): JmapMailSource {
        val client = JmapClient(stalwartLike(api = ::api))
        return JmapMailSource(JmapConnections(client, store), store, zone = { TimeZone.UTC })
    }

    @Test
    fun inboxMessagesAreMappedForTheExistingInbox() =
        runTest {
            val store = InMemoryLoginStore().apply { save(login, "secret") }
            val snapshot = source(store).inbox()
            assertTrue(snapshot.unavailable.isEmpty())
            val (first, second) = snapshot.messages
            assertEquals("ada/m1", first.id)
            assertEquals("Grace Hopper", first.sender.name)
            assertEquals("Attached is the first draft.", first.body)
            assertEquals("2026-10-04", first.received.date)
            assertEquals("09:30", first.received.time)
            assertEquals("Inbox", first.location.mailbox, "The inbox role maps to the app's Inbox, whatever its name")
            assertEquals("Ada", first.location.account)
            assertTrue(first.attributes.unread)
            assertEquals(listOf("draft.pdf"), first.attributes.attachments)
            assertEquals("ada@example.org", second.sender.name)
            assertEquals("Notes", second.body)
            assertEquals("", second.subject)
            assertTrue(!second.attributes.unread)
        }

    @Test
    fun aLoginThatFailsIsReportedWithoutHidingOthers() =
        runTest {
            val other = login.copy(id = "grace", name = "Grace", username = "grace@example.org")
            val store =
                InMemoryLoginStore().apply {
                    save(login, "secret")
                    save(other, "wrong")
                }
            val snapshot = source(store).inbox()
            assertEquals(listOf(UnavailableAccount("Grace", InboxFailure.SignInRejected)), snapshot.unavailable)
            assertEquals(2, snapshot.messages.size)
        }

    @Test
    fun aMissingSecretAsksTheUserToReconnect() =
        runTest {
            val withoutSecret = SecretlessStore(login)
            val snapshot =
                JmapMailSource(
                    JmapConnections(JmapClient(stalwartLike()), withoutSecret),
                    withoutSecret,
                ).inbox()
            assertEquals(listOf(UnavailableAccount("Ada", InboxFailure.CredentialsUnavailable)), snapshot.unavailable)
        }
}

/** A login whose secret is gone, as after a platform key was invalidated. */
private class SecretlessStore(
    private val login: JmapLogin,
) : LoginStore {
    override fun logins() = listOf(login)

    override fun secret(id: String) = LoginSecret.Unreadable()

    override fun save(
        login: JmapLogin,
        secret: String,
    ) = Unit

    override fun remove(id: String) = Unit
}
