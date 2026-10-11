package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class InboxPillTest {
    @Test
    fun pillsAreSavedInTheirOrder() {
        val pills =
            listOf(
                InboxPill.ReadLater,
                InboxPill.Filter.Flagged,
                InboxPill.Role("archive"),
                InboxPill.Folder("work/ada", "M7"),
            )
        assertEquals(pills, InboxPill.decode(InboxPill.encode(pills)))
        assertEquals(emptyList(), InboxPill.decode(InboxPill.encode(emptyList())), "No pills is a choice too")
    }

    @Test
    fun savedValuesThisVersionDoesNotKnowAreLeftOut() {
        val saved = "unread\ncategory:social\nrole:\nfolder:ada\nfolder:/M7\nunread"
        assertEquals(listOf(InboxPill.Filter.Unread), InboxPill.decode(saved))
    }

    @Test
    fun aNewRowHasReadLaterOnlyWhileItIsOn() {
        val filters = listOf(InboxPill.Filter.Unread, InboxPill.Filter.Attachments)
        assertEquals(filters, InboxPill.defaults(readLater = false))
        assertEquals(filters + InboxPill.ReadLater, InboxPill.defaults(readLater = true))
    }
}
