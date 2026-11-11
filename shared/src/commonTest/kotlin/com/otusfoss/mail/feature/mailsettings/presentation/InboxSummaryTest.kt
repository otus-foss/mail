package com.otusfoss.mail.feature.mailsettings.presentation

import com.otusfoss.mail.core.messages.presentation.ownFolders
import com.otusfoss.mail.core.settings.domain.InboxContents
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
