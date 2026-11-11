package com.otusfoss.mail.app

import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.domain.ReadStatus
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.ListBase
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFiltersTest {
    @Test
    fun eachPageKeepsItsOwnFilters() {
        val filters = PageFilters()
        val unread = InboxFilters(status = ReadStatus.Unread)
        filters.keep(MailPage.Inbox, unread)
        assertEquals(
            unread,
            filters.of(MailPage.Inbox, InboxFilters()),
            "Coming back to the page shows its filters again",
        )
        assertEquals(InboxFilters(), filters.of(MailPage.Folder("ada", "f"), InboxFilters()))
    }

    @Test
    fun conversationsStartWithConversationsOnly() {
        val starting = ListBase.conversations(emptyList()).startingFilters
        assertEquals(
            InboxFilters(quick = setOf(QuickFacet.Conversations)),
            PageFilters().of(MailPage.Conversations, starting),
        )
    }
}
