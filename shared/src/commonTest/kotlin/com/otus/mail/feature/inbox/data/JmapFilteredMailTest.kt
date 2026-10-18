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
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else EMAILS.also { bodies += body } }
            val query = MailQuery(InboxFilters(show = setOf(ShowFacet.Unread), sort = FilterSort.OldestFirst))
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
    fun waitingForReplyKeepsOnlyMailFromTheUser() =
        runTest {
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else MIXED }
            val query =
                MailQuery(
                    InboxFilters(show = setOf(ShowFacet.WaitingForReply)),
                    ownAddresses = setOf("ada@example.org"),
                    logins = setOf("ada"),
                )
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf("mine"), messages.map { it.emailId })
        }

    @Test
    fun countsSumEachLoginsTotal() =
        runTest {
            val bodies = mutableListOf<String>()
            val source = source { body -> if ("Mailbox/get" in body) MAILBOXES else COUNT.also { bodies += body } }
            assertEquals(14L, source.count(MailScope.Inbox, MailQuery(InboxFilters(show = setOf(ShowFacet.Flagged)))))
            assertTrue(bodies.all { "\"limit\":0" in it && "\"calculateTotal\":true" in it })
            assertEquals(7L, source.count(MailScope.Inbox, MailQuery(logins = setOf("bo"))))
        }

    private fun source(respond: (String) -> String) =
        stalwartLike(api = respond).let {
            JmapMailSource(JmapConnections(JmapClient(it), store), store)
        }

    private companion object {
        const val MAILBOXES = """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
            {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
        const val EMAILS = """{"methodResponses": [["Email/query", {"ids": []}, "c0"],
            ["Email/get", {"state": "e", "list": []}, "c1"]]}"""
        const val MIXED = """{"methodResponses": [["Email/query", {"ids": ["mine", "theirs"]}, "c0"],
            ["Email/get", {"state": "e", "list": [
            {"id": "mine", "receivedAt": "2026-10-18T09:00:00Z", "from": [{"email": "Ada@example.org"}]},
            {"id": "theirs", "receivedAt": "2026-10-18T08:00:00Z", "from": [{"email": "bo@example.org"}]}]}, "c1"]]}"""
        const val COUNT = """{"methodResponses": [["Email/query", {"ids": [], "total": 7}, "c0"]]}"""
    }
}
