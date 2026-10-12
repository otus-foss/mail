package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailboxScope
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
            check("\"messageId\"" in body && "\"cc\"" in body) { "Requests what a reply needs" }
            """{"methodResponses": [
                ["Email/query", {"ids": ["m1", "m2"]}, "c0"],
                ["Email/get", {"state": "e", "list": [
                  {"id": "m1", "keywords": {}, "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
                   "to": [{"name": "Ada", "email": "ada@example.org"}], "cc": [{"email": "team@example.org"}],
                   "messageId": ["m1@example.org"], "references": ["m0@example.org"],
                   "subject": "Compiler draft", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Attached…",
                   "hasAttachment": true, "attachments": [{"partId": "2", "name": "draft.pdf"}],
                   "textBody": [{"partId": "1"}], "bodyValues": {"1": {"value": "Attached is the first draft."}}},
                  {"id": "m2", "keywords": {"${'$'}seen": true, "${'$'}flagged": true}, "from": [{"email": "ada@example.org"}],
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
            val snapshot = source(store).messages(MailScope.Inbox)
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
            assertEquals(listOf("Ada", "team@example.org"), (first.to + first.cc).map { it.name })
            assertEquals(listOf("m1@example.org"), first.messageIds)
            assertEquals(listOf("m0@example.org"), first.references)
            assertTrue(second.to.isEmpty() && second.messageIds.isEmpty())
            assertEquals("ada@example.org", second.sender.name)
            assertEquals("Notes", second.body)
            assertEquals("", second.subject)
            assertTrue(!second.attributes.unread)
            assertTrue(!first.attributes.flagged)
            assertTrue(second.attributes.flagged)
        }

    @Test
    fun aFolderLoadsOnlyItsAccountAndKeepsItsName() =
        runTest {
            val queried = mutableListOf<String>()
            val folderApi = { body: String ->
                queried += body
                if ("Mailbox/get" in body) {
                    """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                        {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
                } else {
                    """{"methodResponses": [["Email/query", {"ids": ["m1"]}, "c0"],
                        ["Email/get", {"state": "e", "list": [{"id": "m1", "subject": "Order",
                         "receivedAt": "2026-10-04T09:30:00Z"}]}, "c1"]]}"""
                }
            }
            val other = login.copy(id = "grace", name = "Grace", username = "grace@example.org")
            val store =
                InMemoryLoginStore().apply {
                    save(login, "secret")
                    save(other, "wrong")
                }
            val client = JmapClient(stalwartLike(api = folderApi))
            val source = JmapMailSource(JmapConnections(client, store), store, zone = { TimeZone.UTC })
            val snapshot = source.messages(MailScope.Folder("ada", "r"))
            assertTrue(snapshot.unavailable.isEmpty(), "Other accounts are not loaded for a folder")
            val message = snapshot.messages.single()
            assertEquals("Receipts", message.location.mailbox)
            assertEquals("r", message.location.mailboxId)
            assertTrue(queried.any { "\"inMailbox\":\"r\"" in it })
        }

    @Test
    fun chosenMailboxesLoadOnceEachAndAllMailboxesLoadEveryOne() =
        runTest {
            val queried = mutableListOf<String>()
            val mailboxApi = { body: String ->
                queried += body
                if ("Mailbox/get" in body) {
                    """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                        {"id": "a", "name": "Posteingang", "role": "inbox"}, {"id": "s", "name": "Sent"},
                        {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
                } else {
                    val ids = if ("\"inMailbox\":\"a\"" in body) "\"m1\", \"m2\"" else "\"m1\""
                    """{"methodResponses": [["Email/query", {"ids": [$ids]}, "c0"],
                        ["Email/get", {"state": "e", "list": [${ids.split(
                        ", ",
                    ).joinToString { email(it) }}]}, "c1"]]}"""
                }
            }
            val store = InMemoryLoginStore().apply { save(login, "secret") }
            val client = JmapClient(stalwartLike(api = mailboxApi))
            val source = JmapMailSource(JmapConnections(client, store), store, zone = { TimeZone.UTC })
            val snapshot =
                source.messages(
                    MailScope.Mailboxes(setOf(MailboxScope(null, "Inbox"), MailboxScope(null, "Receipts"))),
                )
            assertEquals(listOf("ada/m1", "ada/m2"), snapshot.messages.map { it.id })
            assertEquals(
                "Inbox",
                snapshot.messages
                    .first()
                    .location.mailbox,
                "A message in both is listed under the first",
            )
            assertTrue(queried.any { "\"inMailbox\":\"r\"" in it })
            assertTrue(queried.none { "\"inMailbox\":\"s\"" in it }, "Mailboxes that were not chosen are not loaded")
            source.messages(MailScope.AllMailboxes)
            assertTrue(queried.any { "\"inMailbox\":\"s\"" in it })
        }

    private fun email(id: String) = """{"id": $id, "subject": "Order", "receivedAt": "2026-10-04T09:30:00Z"}"""

    @Test
    fun aFolderPillLoadsEachChosenFolderOfItsAccountsOnly() =
        runTest {
            val queried = mutableListOf<String>()
            val folderApi = { body: String ->
                queried += body
                if ("Mailbox/get" in body) {
                    """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"},
                        {"id": "r", "name": "Receipts"}, {"id": "l", "name": "Read later"}]}, "c0"]]}"""
                } else {
                    """{"methodResponses": [["Email/query", {"ids": ["m1"]}, "c0"],
                        ["Email/get", {"state": "e", "list": [{"id": "m1", "subject": "Order",
                         "receivedAt": "2026-10-04T09:30:00Z"}]}, "c1"]]}"""
                }
            }
            val store =
                InMemoryLoginStore().apply {
                    save(login, "secret")
                    save(login.copy(id = "grace", name = "Grace", username = "grace@example.org"), "wrong")
                }
            val source = JmapMailSource(JmapConnections(JmapClient(stalwartLike(api = folderApi)), store), store)
            val chosen = setOf(MailScope.Folder("ada", "r"), MailScope.Folder("ada", "l"))
            val snapshot = source.messages(MailScope.Folders(chosen))
            assertTrue(snapshot.unavailable.isEmpty(), "Accounts without a chosen folder are not loaded")
            assertEquals(listOf("ada/m1"), snapshot.messages.map { it.id }, "A message in both folders is listed once")
            assertTrue(queried.any { "\"inMailbox\":\"r\"" in it } && queried.any { "\"inMailbox\":\"l\"" in it })
            assertTrue(queried.none { "\"inMailbox\":\"a\"" in it })
        }

    @Test
    fun aRoleLoadsThatFolderOfEveryAccountUnderOneName() =
        runTest {
            val roleApi = { body: String ->
                if ("Mailbox/get" in body) {
                    """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                        {"id": "a", "name": "Inbox", "role": "inbox"},
                        {"id": "j", "name": "Junk Mail", "role": "junk"}]}, "c0"]]}"""
                } else {
                    check("\"inMailbox\":\"j\"" in body) { "Queries the folder with the junk role" }
                    """{"methodResponses": [["Email/query", {"ids": ["m1"]}, "c0"],
                        ["Email/get", {"state": "e", "list": [{"id": "m1", "subject": "Prize",
                         "receivedAt": "2026-10-04T09:30:00Z"}]}, "c1"]]}"""
                }
            }
            val store =
                InMemoryLoginStore().apply {
                    save(login, "secret")
                    save(login.copy(id = "work", name = "Work"), "secret")
                }
            val client = JmapClient(stalwartLike(api = roleApi))
            val source = JmapMailSource(JmapConnections(client, store), store, zone = { TimeZone.UTC })
            val snapshot = source.messages(MailScope.Role("junk", "Spam"))
            assertTrue(snapshot.unavailable.isEmpty())
            assertEquals(setOf("ada", "work"), snapshot.messages.map { it.location.accountId }.toSet())
            assertTrue(snapshot.messages.all { it.location.mailbox == "Spam" && it.location.mailboxId == "j" })
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
            val snapshot = source(store).messages(MailScope.Inbox)
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
                ).messages(MailScope.Inbox)
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
