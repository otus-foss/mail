package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxContents
import kotlin.test.Test
import kotlin.test.assertEquals

class InboxSummaryTest {
    @Test
    fun theRowNamesEachChoiceAndEverythingAsAllMail() {
        assertEquals("Inbox", inboxSummary(InboxContents()))
        assertEquals("Inbox + Spam", inboxSummary(InboxContents(spam = true)))
        assertEquals("Inbox + Spam + Trash", inboxSummary(InboxContents(spam = true, trash = true)))
        assertEquals("Inbox + Archive + Your folders", inboxSummary(InboxContents(archive = true, ownFolders = true)))
        assertEquals(
            "All mail",
            inboxSummary(InboxContents(spam = true, trash = true, archive = true, ownFolders = true)),
        )
    }
}
