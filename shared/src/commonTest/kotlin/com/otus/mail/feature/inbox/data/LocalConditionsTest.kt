package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocalConditionsTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    private fun check(filters: InboxFilters) = localCheck(MailQuery(filters = filters), "ada", now, TimeZone.UTC)

    private fun row(
        attributes: MailAttributes = MailAttributes(),
        to: String = "ada@example.org",
        hoursAgo: Int = 1,
    ) = ListedRow(
        MailMessage(
            id = "m",
            sender = MailSender("Grace", "grace@example.org"),
            subject = "Hi",
            body = "",
            received = ReceivedAt("2026-10-27", "11:00"),
            location = MailLocation("Inbox", "ada", mailboxRole = MailboxRole.Inbox),
            attributes = attributes,
            to = listOf(MailSender("Ada", to)),
        ),
        now - hoursAgo.hours,
    )

    @Test
    fun aRowPassesWhenItsMarksMeetEveryChoice() {
        val unreadFlagged =
            requireNotNull(check(InboxFilters(status = ReadStatus.Unread, quick = setOf(QuickFacet.Flagged))))
        assertTrue(unreadFlagged(row(MailAttributes(unread = true, flagged = true))))
        assertFalse(unreadFlagged(row(MailAttributes(unread = true))))
        assertFalse(unreadFlagged(row(MailAttributes(flagged = true))))
    }

    @Test
    fun recipientsAndDatesAreCheckedOnTheDevice() {
        val toAda = requireNotNull(check(InboxFilters(to = setOf("Ada@example.org"))))
        assertTrue(toAda(row()))
        assertFalse(toAda(row(to = "bob@example.org")))
        val today = requireNotNull(check(InboxFilters(date = DateFilter.Today)))
        assertEquals(listOf(true, false), listOf(1, 20).map { today(row(hoursAgo = it)) })
    }

    @Test
    fun whatTheDeviceDoesNotKeepWaitsForTheServer() {
        assertNull(check(InboxFilters(size = SizeFilter.Over1Mb)))
        assertNull(check(InboxFilters(types = setOf(TypeFacet.Newsletters))))
        assertNull(check(InboxFilters(quick = setOf(QuickFacet.AwaitingReply))))
        assertNull(check(InboxFilters(to = setOf("ada@example.org"), receivedIn = setOf(RecipientField.Bcc))))
    }
}
