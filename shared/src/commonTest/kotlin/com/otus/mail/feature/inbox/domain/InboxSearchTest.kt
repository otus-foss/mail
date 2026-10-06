package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxSearchTest {
    private val messages =
        listOf(
            MailMessage(
                "older",
                MailSender("Alex", "alex@example.com"),
                "Design review",
                "The prototype is ready",
                ReceivedAt("2026-10-01", "09:00"),
                MailLocation("Inbox", "Work"),
                MailAttributes(true, listOf("draft.pdf")),
            ),
            MailMessage(
                "newer",
                MailSender("Sam", "sam@example.com"),
                "Weekend plans",
                "A walk by the lake",
                ReceivedAt("2026-10-03", "10:00"),
                MailLocation("Inbox", "Personal"),
            ),
            MailMessage(
                "archive",
                MailSender("Alex", "alex@example.com"),
                "Design notes",
                "Archived prototype",
                ReceivedAt("2026-10-02", "12:00"),
                MailLocation("Archive", "Work"),
            ),
        )

    @Test
    fun blankQueryReturnsInboxNewestFirst() {
        assertEquals(listOf("newer", "older"), results(InboxSearch(query = "  ")))
    }

    @Test
    fun caseInsensitiveTermsCanSpanSenderSubjectAndBody() {
        assertEquals(listOf("older"), results(InboxSearch(query = " ALEX \t DESIGN  prototype ")))
        assertEquals(listOf("newer"), results(InboxSearch(query = "sam@example.com")))
    }

    @Test
    fun allTermsAndAllFiltersMustMatch() {
        assertTrue(results(InboxSearch(query = "Alex lake")).isEmpty())
        assertEquals(listOf("older"), results(InboxSearch(unreadOnly = true, attachmentsOnly = true)))
        assertTrue(results(InboxSearch(query = "Weekend", unreadOnly = true)).isEmpty())
    }

    @Test
    fun explicitMailboxAndAccountScopeAreRespected() {
        assertEquals(listOf("archive", "older"), results(InboxSearch(mailboxes = emptySet(), accounts = setOf("Work"))))
        assertEquals(listOf("archive"), results(InboxSearch(mailboxes = setOf(MailboxScope(null, "Archive")))))
        assertEquals(listOf("newer"), results(InboxSearch(accounts = setOf("Personal"))))
    }

    @Test
    fun peopleAndDateFiltersCombineWithQuery() {
        assertEquals(
            listOf("older"),
            results(InboxSearch(senders = setOf("alex@example.com"), dateStart = "2026-10-01", dateEnd = "2026-10-01")),
        )
        assertTrue(
            results(
                InboxSearch(senders = setOf("sam@example.com"), dateStart = "2026-10-01", dateEnd = "2026-10-01"),
            ).isEmpty(),
        )
    }

    @Test
    fun emptySourceAndPunctuationAreHandledLiterally() {
        assertTrue(searchMessages(emptyList(), InboxSearch(query = "hello")).isEmpty())
        assertTrue(results(InboxSearch(query = ".*")).isEmpty())
    }

    @Test
    fun multipleSelectionsAreOrWithinAFilterAndAndAcrossFilters() {
        val criteria =
            InboxSearch(
                senders = setOf("alex@example.com", "sam@example.com"),
                accounts = setOf("Work", "Personal"),
                dateStart = "2026-10-01",
                dateEnd = "2026-10-03",
            )
        assertEquals(listOf("newer", "older"), results(criteria))
        assertEquals(listOf("older"), results(criteria.copy(accounts = setOf("Work"))))
        assertEquals(listOf("newer"), results(criteria.copy(dateStart = "2026-10-03")))
    }

    @Test
    fun mailboxSelectionIncludesAccountIdentity() {
        assertEquals(listOf("older"), results(InboxSearch(mailboxes = setOf(MailboxScope("Work", "Inbox")))))
        assertEquals(listOf("newer"), results(InboxSearch(mailboxes = setOf(MailboxScope("Personal", "Inbox")))))
        assertEquals(listOf("newer", "archive", "older"), results(InboxSearch(mailboxes = emptySet())))
    }

    private fun results(search: InboxSearch): List<String> = searchMessages(messages, search).map { it.id }

    @Test
    fun selectedSendersResolveNamesAndKeepUnknownAddresses() {
        val senders = selectedSenders(messages, setOf("alex@example.com", "gone@example.com"))
        assertEquals(listOf(MailSender("Alex", "alex@example.com"), MailSender("", "gone@example.com")), senders)
    }
}
