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
import com.otus.mail.feature.inbox.domain.ShowFacet
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
            val server = FakeMailServer(MAILBOXES)
            val query = MailQuery(InboxFilters(show = setOf(ShowFacet.Unread), sort = FilterSort.OldestFirst))
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
    fun waitingForReplyKeepsOnlyMailFromTheUser() =
        runTest {
            val source = FakeMailServer(MAILBOXES, MIXED).source(store)
            val query =
                MailQuery(
                    InboxFilters(show = setOf(ShowFacet.WaitingForReply)),
                    addresses = mapOf("ada" to setOf("ada@example.org")),
                    logins = setOf("ada"),
                )
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf("mine"), messages.map { it.emailId })
        }

    @Test
    fun calendarInvitesAreCheckedOnTheLoadedMailAndNotCounted() =
        runTest {
            val source = FakeMailServer(MAILBOXES, INVITES).source(store)
            val query = MailQuery(InboxFilters(show = setOf(ShowFacet.CalendarInvites)), logins = setOf("ada"))
            assertEquals(listOf("invite"), source.messages(MailScope.Inbox, query).messages.map { it.emailId })
            assertEquals(null, source.count(MailScope.Inbox, query))
        }

    @Test
    fun countsSumEachLoginsTotal() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOX_REPLY else COUNT.also { bodies += body } }
            assertEquals(14L, source.count(MailScope.Inbox, MailQuery(InboxFilters(show = setOf(ShowFacet.Flagged)))))
            assertTrue(bodies.all { "\"limit\":0" in it && "\"calculateTotal\":true" in it })
            assertEquals(7L, source.count(MailScope.Inbox, MailQuery(logins = setOf("bo"))))
        }

    private fun source(respond: (String) -> String) =
        stalwartLike(api = respond).let {
            JmapMailSource(JmapConnections(JmapClient(it), store), store)
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}"""
        const val MAILBOX_REPLY = """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
            $MAILBOXES]}, "c0"]]}"""
        const val MIXED = """
            {"id": "mine", "receivedAt": "2026-10-18T09:00:00Z", "mailboxIds": {"a": true},
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
