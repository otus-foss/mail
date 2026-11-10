package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.settings.domain.FilterSection
import com.otus.mail.core.settings.domain.InboxPill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListKindTest {
    @Test
    fun theInboxOffersEveryFilter() {
        assertTrue(FilterSection.entries.all(ListKind.Inbox::offers))
        assertTrue(QuickFacet.entries.all(ListKind.Inbox::offers))
        assertTrue(InboxPill.Filter.entries.all(ListKind.Inbox::offers))
        assertTrue(ListKind.Inbox.offersTypes)
    }

    @Test
    fun theUsersOwnMailHasNoStatusOrMailTypes() {
        listOf(ListKind.Drafts, ListKind.Sent, ListKind.Scheduled).forEach { kind ->
            assertFalse(kind.offers(FilterSection.Status))
            assertFalse(kind.offers(InboxPill.Filter.Unread))
            assertFalse(kind.offersTypes)
        }
        assertTrue(ListKind.Spam.offers(FilterSection.Status))
    }

    @Test
    fun unsentMailAndSpamHaveNoThreadFilters() {
        listOf(ListKind.Drafts, ListKind.Scheduled, ListKind.Spam).forEach { kind ->
            assertFalse(kind.offers(QuickFacet.NeedsMyReply))
            assertFalse(kind.offers(QuickFacet.Conversations))
            assertFalse(kind.offers(InboxPill.Filter.Conversations))
            assertTrue(kind.offers(QuickFacet.Attachments))
        }
    }

    @Test
    fun unsentMailFiltersOnlyByAttachments() {
        listOf(ListKind.Drafts, ListKind.Scheduled).forEach { kind ->
            assertEquals(listOf(QuickFacet.Attachments), QuickFacet.entries.filter(kind::offers))
            assertEquals(listOf(InboxPill.Filter.Attachments), InboxPill.Filter.entries.filter(kind::offers))
        }
    }

    @Test
    fun onlyTheInboxNarrowsToReadLater() {
        assertEquals(listOf(ListKind.Inbox), ListKind.entries.filter { it.offers(QuickFacet.ReadLater) })
    }

    @Test
    fun rolesWithoutRulesOfTheirOwnFilterAsFolders() {
        assertEquals(ListKind.Sent, ListKind.of(MailboxRole.Sent))
        assertEquals(ListKind.Scheduled, ListKind.of(MailboxRole.Scheduled))
        assertEquals(ListKind.Spam, ListKind.of(MailboxRole.Junk))
        assertEquals(ListKind.Folder, ListKind.of(MailboxRole.Trash))
        assertEquals(ListKind.Folder, ListKind.of(null))
    }
}
