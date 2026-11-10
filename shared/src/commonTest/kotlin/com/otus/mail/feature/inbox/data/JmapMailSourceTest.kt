package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.id
import com.otus.mail.testing.mailSource
import com.otus.mail.testing.source
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapMailSourceTest {
    private val login = ADA_LOGIN
    private val other = login.copy(id = "grace", name = "Grace", username = "grace@example.org")

    private val inbox =
        FakeMailServer(
            """{"id": "s", "name": "Sent Items", "role": "sent"},
            {"id": "a", "name": "Posteingang", "role": "inbox"}""",
            """
            {"id": "m1", "mailboxIds": {"a": true}, "keywords": {},
             "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
             "to": [{"name": "Ada", "email": "ada@example.org"}], "cc": [{"email": "team@example.org"}],
             "messageId": ["m1@example.org"], "references": ["m0@example.org"],
             "subject": "Compiler draft", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Attached…",
             "hasAttachment": true, "attachments": [{"partId": "2", "name": "draft.pdf"}]},
            {"id": "m2", "mailboxIds": {"a": true}, "keywords": {"${'$'}seen": true, "${'$'}flagged": true},
             "from": [{"email": "ada@example.org"}],
             "subject": null, "receivedAt": "2026-10-03T23:05:00Z", "preview": "Notes",
             "hasAttachment": false}""",
        )

    private fun store(vararg wrong: JmapLogin) =
        InMemoryLoginStore().apply {
            save(login, "secret")
            wrong.forEach { save(it, "wrong") }
        }

    @Test
    fun inboxMessagesAreMappedForTheExistingInbox() =
        runTest {
            val snapshot = inbox.source(store()).messages(MailScope.Inbox)
            assertTrue(snapshot.unavailable.isEmpty())
            val (first, second) = snapshot.messages
            assertEquals("ada/m1", first.id)
            assertEquals("Grace Hopper", first.sender.name)
            assertEquals("Attached…", first.body)
            assertEquals("Attached…", first.preview)
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
            val query = inbox.bodies.single { "Email/query" in it }
            assertTrue("\"inMailbox\":\"a\"" in query && "\"collapseThreads\":true" in query, "One row per thread")
            val rows = inbox.bodies.single { "\"preview\"" in it }
            assertTrue("\"messageId\"" in rows && "\"cc\"" in rows, "Requests what a reply needs")
            assertTrue("\"Thread/get\"" in rows && "\"#ids\"" in rows, "Threads chain by back-reference")
            assertTrue(inbox.bodies.none { "\"textBody\"" in it }, "The list shows previews and loads no bodies")
        }

    @Test
    fun aFolderLoadsOnlyItsAccountAndKeepsItsName() =
        runTest {
            val server = FakeMailServer(MAILBOXES, email("m1", "r"))
            val snapshot = server.source(store(other)).messages(MailScope.Folder("ada", "r"))
            assertTrue(snapshot.unavailable.isEmpty(), "Other accounts are not loaded for a folder")
            val message = snapshot.messages.single()
            assertEquals("Receipts", message.location.mailbox)
            assertEquals("r", message.location.mailboxId)
            assertTrue(server.bodies.any { "\"inMailbox\":\"r\"" in it })
        }

    @Test
    fun chosenMailboxesLoadOnceEachAndAllMailboxesLoadEveryOne() =
        runTest {
            val emails = email("m1", "a", "r", "s") + ", " + email("m2", "a", receivedAt = "2026-10-04T08:30:00Z")
            val server = FakeMailServer(MAILBOXES, emails)
            val source = server.source(store())
            val snapshot =
                source.messages(
                    MailScope.Mailboxes(setOf(MailboxScope.Inbox, MailboxScope(null, "Receipts"))),
                )
            assertEquals(listOf("ada/m1", "ada/m2"), snapshot.messages.map { it.id })
            assertEquals(
                "Inbox",
                snapshot.messages
                    .first()
                    .location.mailbox,
                "A message in both is listed once, under the first",
            )
            val queried = server.bodies.filter { "Email/query" in it }
            assertTrue(queried.any { "\"inMailbox\":\"r\"" in it })
            assertTrue(queried.none { "\"inMailbox\":\"s\"" in it }, "Mailboxes that were not chosen are not loaded")
            source.messages(MailScope.AllMailboxes)
            assertTrue(server.bodies.any { "\"inMailbox\":\"s\"" in it })
        }

    @Test
    fun aFolderPillLoadsEachChosenFolderOfItsAccountsOnly() =
        runTest {
            val server = FakeMailServer(MAILBOXES, email("m1", "r", "l"))
            val chosen = setOf(MailScope.Folder("ada", "r"), MailScope.Folder("ada", "l"))
            val snapshot = server.source(store(other)).messages(MailScope.Folders(chosen))
            assertTrue(snapshot.unavailable.isEmpty(), "Accounts without a chosen folder are not loaded")
            assertEquals(listOf("ada/m1"), snapshot.messages.map { it.id }, "A message in both folders is listed once")
            val queried = server.bodies.filter { "Email/query" in it }
            assertTrue(queried.any { "\"inMailbox\":\"r\"" in it } && queried.any { "\"inMailbox\":\"l\"" in it })
            assertTrue(queried.none { "\"inMailbox\":\"a\"" in it })
        }

    @Test
    fun aRoleLoadsThatFolderOfEveryAccountUnderOneName() =
        runTest {
            val junk = """, {"id": "j", "name": "Junk Mail", "role": "junk"}"""
            val server = FakeMailServer(MAILBOXES + junk, email("m1", "j"))
            val store =
                InMemoryLoginStore().apply {
                    save(login, "secret")
                    save(login.copy(id = "work", name = "Work"), "secret")
                }
            val snapshot = server.source(store).messages(MailScope.Role(MailboxRole.Junk))
            assertTrue(snapshot.unavailable.isEmpty())
            assertEquals(setOf("ada", "work"), snapshot.messages.map { it.location.accountId }.toSet())
            assertTrue(snapshot.messages.all { it.location.mailbox == "Spam" && it.location.mailboxId == "j" })
        }

    @Test
    fun scheduledListsEachEmailOfAConversationApart() =
        runTest {
            val scheduled = """, {"id": "sc", "name": "Scheduled", "role": "scheduled"}"""
            val replies =
                listOf("m1" to "2026-10-04T09:30:00Z", "m2" to "2026-10-05T09:30:00Z")
                    .joinToString { (id, at) ->
                        email(id, "sc", receivedAt = at).replaceFirst("{", """{"threadId": "t1", """)
                    }
            val server = FakeMailServer(MAILBOXES + scheduled, replies)
            val snapshot = server.source(store()).messages(MailScope.Role(MailboxRole.Scheduled))
            assertEquals(listOf("ada/m2", "ada/m1"), snapshot.messages.map { it.id }, "Each scheduled send has a row")
            assertTrue(snapshot.messages.all { it.conversation?.messageCount == 1 }, "A row stands for its own email")
            val query = server.bodies.single { "Email/query" in it }
            assertTrue("\"collapseThreads\":false" in query)
        }

    @Test
    fun aLoginThatFailsIsReportedWithoutHidingOthers() =
        runTest {
            val snapshot = inbox.source(store(other)).messages(MailScope.Inbox)
            assertEquals(listOf(UnavailableAccount("Grace", MailFailure.SignInRejected)), snapshot.unavailable)
            assertEquals(2, snapshot.messages.size)
        }

    @Test
    fun aMissingSecretAsksTheUserToReconnect() =
        runTest {
            val withoutSecret = SecretlessStore(login)
            val snapshot =
                mailSource(JmapConnections(JmapClient(stalwartLike()), withoutSecret)).messages(MailScope.Inbox)
            assertEquals(listOf(UnavailableAccount("Ada", MailFailure.CredentialsUnavailable)), snapshot.unavailable)
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "s", "name": "Sent"},
            {"id": "r", "name": "Receipts"}, {"id": "l", "name": "Read later"}"""

        fun email(
            id: String,
            vararg mailboxes: String,
            receivedAt: String = "2026-10-04T09:30:00Z",
        ) = """{"id": "$id", "subject": "Order", "receivedAt": "$receivedAt",
            "mailboxIds": {${mailboxes.joinToString { "\"$it\": true" }}}}"""
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
