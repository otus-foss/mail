package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxContents
import kotlin.test.Test
import kotlin.test.assertEquals

class InboxSummaryTest {
    @Test
    fun theRowNamesEachChoiceAndBothAsAllMail() {
        assertEquals("Inbox", inboxSummary(InboxContents()))
        assertEquals("Inbox + Spam", inboxSummary(InboxContents(spam = true)))
        assertEquals("Inbox + Trash", inboxSummary(InboxContents(trash = true)))
        assertEquals("All mail", inboxSummary(InboxContents(spam = true, trash = true)))
    }
}
