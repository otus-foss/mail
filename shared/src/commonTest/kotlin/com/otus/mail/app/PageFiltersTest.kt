package com.otus.mail.app

import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.presentation.FolderKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFiltersTest {
    @Test
    fun eachPageKeepsItsOwnFilters() {
        val filters = PageFilters()
        val unread = InboxFilters(show = setOf(ShowFacet.Unread))
        filters.keep(MailPage.Inbox, unread)
        assertEquals(unread, filters.of(MailPage.Inbox), "Coming back to the page shows its filters again")
        assertEquals(InboxFilters(), filters.of(MailPage.Folder("ada", "f")))
        assertEquals(InboxFilters(), filters.of(MailPage.ReadLater))
    }

    @Test
    fun recentFoldersPutTheLatestFirst() {
        val filters = PageFilters()
        filters.keep(MailPage.Inbox, InboxFilters(folders = setOf(FolderRef("ada", "a"))))
        filters.keep(MailPage.Inbox, InboxFilters(folders = setOf(FolderRef("ada", "b"))))
        filters.keep(MailPage.Inbox, InboxFilters(folders = setOf(FolderRef("ada", "a"))))
        assertEquals(listOf(FolderKey("ada", "a"), FolderKey("ada", "b")), filters.recentFolders)
    }
}
