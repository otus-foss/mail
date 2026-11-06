package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
                MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
                MailAttributes(true, listOf("draft.pdf")),
            ),
            MailMessage(
                "newer",
                MailSender("Sam", "sam@example.com"),
                "Weekend plans",
                "A walk by the lake",
                ReceivedAt("2026-10-03", "10:00"),
                MailLocation("Inbox", "Personal", mailboxRole = MailboxRole.Inbox),
                MailAttributes(flagged = true),
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
    fun aFieldLimitsWhereTermsMatchAndTogglesBackToAnywhere() {
        val subject = InboxSearch(query = "design").toggled(SearchField.Subject)
        assertEquals(listOf("older"), results(subject))
        assertTrue(results(subject.copy(query = "prototype")).isEmpty())
        val body = subject.toggled(SearchField.Body)
        assertEquals(listOf("older"), results(body.copy(query = "prototype")))
        assertTrue(results(body.copy(query = "alex")).isEmpty())
        assertEquals(SearchField.Anywhere, body.toggled(SearchField.Body).field)
    }

    @Test
    fun allTermsAndAllFiltersMustMatch() {
        assertTrue(results(InboxSearch(query = "Alex lake")).isEmpty())
        val unreadWithFiles = InboxFilters(status = ReadStatus.Unread, quick = setOf(QuickFacet.Attachments))
        assertEquals(listOf("older"), results(InboxSearch(), unreadWithFiles))
        assertTrue(results(InboxSearch(query = "Weekend"), InboxFilters(status = ReadStatus.Unread)).isEmpty())
    }

    @Test
    fun aFilterPillTurnsItsFilterOnAndOff() {
        val flagged = InboxFilters().with(InboxPill.Filter.Flagged, on = true)
        assertTrue(flagged.has(InboxPill.Filter.Flagged))
        assertEquals(listOf("newer"), results(InboxSearch(), flagged))
        assertEquals(InboxFilters(), flagged.with(InboxPill.Filter.Flagged, on = false))
    }

    @Test
    fun oldestFirstReversesTheOrder() {
        val all = results(InboxSearch(mailboxes = emptySet()))
        assertEquals(
            all.reversed(),
            results(InboxSearch(mailboxes = emptySet()), InboxFilters(sort = FilterSort.OldestFirst)),
        )
    }

    @Test
    fun conversationsKeepThreadsWhereBothSidesWroteAndSortByTheNewestEmail() {
        val sam = MailSender("Sam", "sam@example.com")

        fun thread(
            lastDate: String,
            exchanged: Boolean,
        ) = MailConversation(
            "t",
            2,
            ThreadEmail(sam, emptyList(), ReceivedAt(lastDate, "08:00")),
            listOf(sam),
            false,
            emptyList(),
            exchanged,
        )
        val replied = messages[0].copy(conversation = thread("2026-10-05", exchanged = true))
        val unanswered = messages[1].copy(conversation = thread("2026-10-04", exchanged = false))
        val conversations = InboxFilters(quick = setOf(QuickFacet.Conversations))
        val shown = searchMessages(listOf(unanswered, replied), InboxSearch(), conversations)
        assertEquals(listOf("older"), shown.map { it.id })
        assertEquals(listOf("older", "newer"), searchMessages(listOf(unanswered, replied), InboxSearch()).map { it.id })
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
        assertEquals(
            listOf("older"),
            results(InboxSearch(mailboxes = setOf(MailboxScope("Work", "Inbox", MailboxRole.Inbox)))),
        )
        assertEquals(
            listOf("newer"),
            results(InboxSearch(mailboxes = setOf(MailboxScope("Personal", "Inbox", MailboxRole.Inbox)))),
        )
        assertEquals(listOf("newer", "archive", "older"), results(InboxSearch(mailboxes = emptySet())))
    }

    @Test
    fun theInboxIsFoundByRoleNotByAFolderOfTheSameName() {
        val namedInbox = MailLocation("Inbox", "Work")
        assertTrue(MailboxScope.Inbox.matches(MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox)))
        assertTrue(!MailboxScope.Inbox.matches(namedInbox), "A folder of the user's named Inbox")
        assertTrue(MailboxScope(null, "Inbox").matches(namedInbox))
    }

    private fun results(
        search: InboxSearch,
        filters: InboxFilters = InboxFilters(),
    ): List<String> = searchMessages(messages, search, filters).map { it.id }

    @Test
    fun selectedSendersResolveNamesAndKeepUnknownAddresses() {
        val senders = selectedSenders(messages, setOf("alex@example.com", "gone@example.com"))
        assertEquals(listOf(MailSender("Alex", "alex@example.com"), MailSender("", "gone@example.com")), senders)
    }

    @Test
    fun mailboxAndAccountChoicesKeepTheirRemoteScope() {
        val default = InboxSearch()
        assertNull(default.copy(query = "plans").scopeToLoad(default))
        assertEquals(
            MailScope.Mailboxes(default.mailboxes, setOf("Work")),
            default.copy(accounts = setOf("Work")).scopeToLoad(default),
        )
        val withArchive = default.copy(mailboxes = default.mailboxes + MailboxScope(null, "Archive"))
        assertEquals(
            MailScope.Mailboxes(setOf(MailboxScope.Inbox, MailboxScope(null, "Archive"))),
            withArchive.scopeToLoad(default),
        )
        assertEquals(MailScope.AllMailboxes, default.copy(mailboxes = emptySet()).scopeToLoad(default))
        val accountMailbox = setOf(MailboxScope("Work", "Inbox", MailboxRole.Inbox))
        assertEquals(
            MailScope.Mailboxes(accountMailbox),
            default.copy(mailboxes = accountMailbox).scopeToLoad(default),
        )
    }

    @Test
    fun selectedAccountsKeepSelectionOrderAndUnknownIds() {
        val accounts = selectedAccounts(messages, setOf("Personal", "gone", "Work"))
        assertEquals(listOf("Personal", "gone", "Work"), accounts.map { it.account })
        val empty = MailLocation("", "Empty account", "empty", "empty@example.org")
        assertEquals(listOf(empty), selectedAccounts(messages, setOf("empty"), listOf(empty)))
    }
}
