package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.FilterSort
import com.otus.mail.core.messages.domain.FolderRef
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.messages.domain.ReadStatus
import com.otus.mail.core.messages.domain.TypeFacet
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.mailSource
import com.otus.mail.testing.source
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapFilteredMailTest {
    private val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")

    // The fake server knows one password, so the second login signs in with the same address.
    private val bo = ada.copy(id = "bo", name = "Bo")
    private val store =
        InMemoryLoginStore().apply {
            save(ada, "secret")
            save(bo, "secret")
        }

    @Test
    fun loadsRunTheFiltersInEachMailboxQuery() =
        runTest {
            val server = FakeMailServer(MAILBOXES)
            val query = MailQuery(InboxFilters(status = ReadStatus.Unread, sort = FilterSort.OldestFirst))
            server.source(store).messages(MailScope.Inbox, query)
            val bodies = server.bodies.filter { "Email/query" in it }
            assertEquals(2, bodies.size, "One inbox query per login")
            assertTrue(bodies.all { "\"notKeyword\":\"\$seen\"" in it && "\"inMailbox\":\"a\"" in it })
            assertTrue(bodies.all { "\"isAscending\":true" in it })
        }

    @Test
    fun chosenFoldersReplaceThePageMailboxesAndSkipOtherLogins() =
        runTest {
            val server = FakeMailServer(MAILBOXES)
            val query =
                MailQuery(
                    InboxFilters(folders = setOf(FolderRef("ada", "r"))),
                    folderIds =
                        mapOf("ada" to setOf("r")),
                )
            server.source(store).messages(MailScope.Inbox, query)
            val bodies = server.bodies.filter { "Email/query" in it }
            assertEquals(1, bodies.size)
            assertTrue("\"inMailbox\":\"r\"" in bodies.single())
        }

    @Test
    fun awaitingReplyAlsoSearchesSentAndKeepsThreadsTheUserWroteLast() =
        runTest {
            val server = FakeMailServer(MAILBOXES, MIXED)
            val source = server.source(store)
            val query =
                MailQuery(
                    InboxFilters(quick = setOf(QuickFacet.AwaitingReply)),
                    addresses = mapOf("ada" to setOf("ada@example.org")),
                    logins = setOf("ada"),
                )
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf("mine"), messages.map { it.emailId })
            val bodies = server.bodies.filter { "Email/query" in it }
            assertTrue(bodies.any { "\"inMailbox\":\"s\"" in it }, "Sent is queried too")
            assertTrue(bodies.none { "\"from\":\"ada@example.org\"" in it }, "No server condition on the sender")
            assertEquals(1L, source.count(MailScope.Inbox, query), "Counted in the loaded threads")
        }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun countsReadEachLoginsMailboxesOnceAndThenKeepThem() =
        runTest {
            // The fake server answers both logins at once, on different threads.
            val mailboxLoads = AtomicInt(0)
            val source =
                source { body ->
                    if ("Mailbox/get" in body) MAILBOX_REPLY.also { mailboxLoads.incrementAndFetch() } else COUNT
                }
            source.count(MailScope.Inbox, MailQuery(InboxFilters(status = ReadStatus.Unread)))
            source.count(MailScope.Inbox, MailQuery(InboxFilters(status = ReadStatus.Read)))
            assertEquals(2, mailboxLoads.load(), "One per login, not one per count")
        }

    @Test
    fun calendarInvitesAreCheckedAndCountedOnTheLoadedMail() =
        runTest {
            val source = FakeMailServer(MAILBOXES, INVITES).source(store)
            val query = MailQuery(InboxFilters(types = setOf(TypeFacet.CalendarInvites)), logins = setOf("ada"))
            assertEquals(listOf("invite"), source.messages(MailScope.Inbox, query).messages.map { it.emailId })
            assertEquals(1L, source.count(MailScope.Inbox, query))
        }

    @Test
    fun countsSumEachLoginsTotal() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOX_REPLY else COUNT.also { bodies += body } }
            assertEquals(14L, source.count(MailScope.Inbox, MailQuery(InboxFilters(quick = setOf(QuickFacet.Flagged)))))
            assertTrue(bodies.all { "\"limit\":0" in it && "\"calculateTotal\":true" in it })
            assertEquals(7L, source.count(MailScope.Inbox, MailQuery(logins = setOf("bo"))))
        }

    private fun source(respond: (String) -> String) =
        stalwartLike(api = respond).let {
            mailSource(JmapConnections(JmapClient(it), store))
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"},
            {"id": "s", "name": "Sent", "role": "sent"}"""
        const val MAILBOX_REPLY = """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
            $MAILBOXES]}, "c0"]]}"""

        // The user's thread has no email in the Inbox, so only the Sent query finds it.
        const val MIXED = """
            {"id": "mine", "receivedAt": "2026-10-18T09:00:00Z", "mailboxIds": {"s": true},
             "from": [{"email": "Ada@example.org"}]},
            {"id": "theirs", "receivedAt": "2026-10-18T08:00:00Z", "mailboxIds": {"a": true},
             "from": [{"email": "bo@example.org"}]}"""
        const val INVITES = """
            {"id": "invite", "receivedAt": "2026-10-18T09:00:00Z", "mailboxIds": {"a": true}, "hasAttachment": true,
             "attachments": [{"partId": "2", "type": "text/calendar", "name": "invite.ics"}]},
            {"id": "plain", "receivedAt": "2026-10-18T08:00:00Z", "mailboxIds": {"a": true}}"""
        const val COUNT = """{"methodResponses": [["Email/query", {"ids": [], "total": 7}, "c0"]]}"""
    }
}
