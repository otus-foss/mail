package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.TypeFacet
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
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
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else EMAILS.also { bodies += body } }
            val query = MailQuery(InboxFilters(status = ReadStatus.Unread, sort = FilterSort.OldestFirst))
            source.messages(MailScope.Inbox, query)
            assertEquals(2, bodies.size, "One inbox query per login")
            assertTrue(bodies.all { "\"notKeyword\":\"\$seen\"" in it && "\"inMailbox\":\"a\"" in it })
            assertTrue(bodies.all { "\"isAscending\":true" in it })
        }

    @Test
    fun chosenFoldersReplaceThePageMailboxesAndSkipOtherLogins() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else EMAILS.also { bodies += body } }
            val query =
                MailQuery(
                    InboxFilters(folders = setOf(FolderRef("ada", "r"))),
                    folderIds =
                        mapOf("ada" to setOf("r")),
                )
            source.messages(MailScope.Inbox, query)
            assertEquals(1, bodies.size)
            assertTrue("\"inMailbox\":\"r\"" in bodies.single())
        }

    @Test
    fun awaitingReplyAlsoSearchesSentAndKeepsThreadsTheUserWroteLast() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else MIXED.also { bodies += body } }
            val query =
                MailQuery(
                    InboxFilters(quick = setOf(QuickFacet.AwaitingReply)),
                    addresses = mapOf("ada" to setOf("ada@example.org")),
                    logins = setOf("ada"),
                )
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf("mine"), messages.map { it.emailId })
            assertEquals(2, bodies.size, "The inbox and Sent")
            assertTrue(bodies.any { "\"inMailbox\":\"s\"" in it })
            assertTrue(bodies.none { "\"from\":\"ada@example.org\"" in it }, "No server condition on the sender")
            assertEquals(null, source.count(MailScope.Inbox, query), "Only the loaded threads can tell")
        }

    @Test
    fun countsReadEachLoginsMailboxesOncePerSession() =
        runTest {
            var mailboxLoads = 0
            val source =
                source { body ->
                    if ("Mailbox/get" in body) MAILBOXES.also { mailboxLoads++ } else COUNT
                }
            source.beginCounting()
            source.count(MailScope.Inbox, MailQuery(InboxFilters(status = ReadStatus.Unread)))
            source.count(MailScope.Inbox, MailQuery(InboxFilters(status = ReadStatus.Read)))
            assertEquals(2, mailboxLoads, "One per login, not one per count")
            source.beginCounting()
            source.count(MailScope.Inbox, MailQuery(InboxFilters(status = ReadStatus.Read)))
            assertEquals(4, mailboxLoads, "A new session reads them again")
        }

    @Test
    fun calendarInvitesAreCheckedOnTheLoadedMailAndNotCounted() =
        runTest {
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else INVITES }
            val query = MailQuery(InboxFilters(types = setOf(TypeFacet.CalendarInvites)), logins = setOf("ada"))
            assertEquals(listOf("invite"), source.messages(MailScope.Inbox, query).messages.map { it.emailId })
            assertEquals(null, source.count(MailScope.Inbox, query))
        }

    @Test
    fun countsSumEachLoginsTotal() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else COUNT.also { bodies += body } }
            assertEquals(14L, source.count(MailScope.Inbox, MailQuery(InboxFilters(quick = setOf(QuickFacet.Flagged)))))
            assertTrue(bodies.all { "\"limit\":0" in it && "\"calculateTotal\":true" in it })
            assertEquals(7L, source.count(MailScope.Inbox, MailQuery(logins = setOf("bo"))))
        }

    private fun source(respond: (String) -> String) =
        stalwartLike(api = respond).let {
            JmapMailSource(JmapConnections(JmapClient(it), store), store)
        }

    private companion object {
        const val MAILBOXES = """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
            {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"},
            {"id": "s", "name": "Sent", "role": "sent"}]}, "c0"]]}"""
        val EMAILS = emailListResponse("", "")
        val MIXED =
            emailListResponse(
                "\"mine\", \"theirs\"",
                """{"id": "mine", "receivedAt": "2026-10-18T09:00:00Z", "from": [{"email": "Ada@example.org"}]},
                {"id": "theirs", "receivedAt": "2026-10-18T08:00:00Z", "from": [{"email": "bo@example.org"}]}""",
            )
        val INVITES =
            emailListResponse(
                "\"invite\", \"plain\"",
                """{"id": "invite", "receivedAt": "2026-10-18T09:00:00Z", "hasAttachment": true,
                 "attachments": [{"partId": "2", "type": "text/calendar", "name": "invite.ics"}]},
                {"id": "plain", "receivedAt": "2026-10-18T08:00:00Z"}""",
            )
        const val COUNT = """{"methodResponses": [["Email/query", {"ids": [], "total": 7}, "c0"]]}"""
    }
}
