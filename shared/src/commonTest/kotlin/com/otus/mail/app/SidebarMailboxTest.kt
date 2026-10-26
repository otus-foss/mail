package com.otus.mail.app

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SENT_ROLE
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarMailboxTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = INBOX_ROLE),
                MailFolder("ada", "s", "Sent", role = SENT_ROLE),
                MailFolder("ada", "w", "Work"),
                MailFolder("ada", "c", "Clients", parentId = "w"),
            ),
        )

    @Test
    fun theInboxLeadsThenPinsShowInOrderAndMissingOnesAreSkipped() {
        val pinned =
            listOf(
                SidebarPin.Folder(listOf("work", "clients")),
                SidebarPin.ReadLater,
                SidebarPin.Role("sent"),
                SidebarPin.Folder(listOf("gone")),
            )
        val mailboxes = sidebarMailboxes(listOf(ada), pinned = pinned)
        assertEquals(listOf("Inbox", "Clients", "Sent"), mailboxes.map { it.title })
        assertEquals(MailPage.Shared(listOf("work", "clients")), mailboxes[1].page)
    }

    @Test
    fun everyFolderCanBePinnedAfterConversationsAndTheSystemFolders() {
        assertEquals(
            listOf(
                SidebarPin.Conversations,
                SidebarPin.Contacts,
                SidebarPin.Role("sent"),
                SidebarPin.Folder(listOf("work")),
                SidebarPin.Folder(listOf("work", "clients")),
            ),
            pinnableMailboxes(listOf(ada)).map { it.first },
        )
    }
}
