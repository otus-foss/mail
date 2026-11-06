package com.otus.mail.app

import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.messages.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.InboxFilters
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFiltersTest {
    @Test
    fun eachPageKeepsItsOwnFilters() {
        val filters = PageFilters()
        val unread = InboxFilters(status = ReadStatus.Unread)
        filters.keep(MailPage.Inbox, unread)
        assertEquals(unread, filters.of(MailPage.Inbox), "Coming back to the page shows its filters again")
        assertEquals(InboxFilters(), filters.of(MailPage.Folder("ada", "f")))
        assertEquals(InboxFilters(), filters.of(MailPage.ReadLater))
        assertEquals(InboxFilters(quick = setOf(QuickFacet.Conversations)), filters.of(MailPage.Conversations))
    }
}
