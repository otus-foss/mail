package com.otus.mail.app

import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.inbox.domain.PinSize
import com.otus.mail.feature.inbox.domain.SidebarLayout
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
    fun pinsShowInOrderAtTheirSizesAndMissingOnesAreSkipped() {
        val pinned =
            listOf(
                SidebarPin.Inbox,
                SidebarPin.Folder(listOf("work", "clients")),
                SidebarPin.ReadLater,
                SidebarPin.Role("sent"),
                SidebarPin.Folder(listOf("gone")),
            )
        val layout = SidebarLayout(pinned = pinned, sizes = mapOf(SidebarPin.Role("sent") to PinSize.Wide))
        val mailboxes = sidebarMailboxes(listOf(ada), layout = layout)
        assertEquals(listOf("Inbox", "Clients", "Sent"), mailboxes.map { it.title })
        assertEquals(MailPage.Shared(listOf("work", "clients")), mailboxes[1].page)
        assertEquals(listOf(PinSize.Full, PinSize.Small, PinSize.Wide), mailboxes.map { it.size })
    }

    @Test
    fun anUnpinnedInboxLeavesTheSidebar() {
        val mailboxes = sidebarMailboxes(listOf(ada), layout = SidebarLayout(pinned = listOf(SidebarPin.Role("sent"))))
        assertEquals(listOf("Sent"), mailboxes.map { it.title })
    }

    @Test
    fun everyFolderCanBePinnedAfterTheInboxConversationsAndTheSystemFolders() {
        assertEquals(
            listOf(
                SidebarPin.Inbox,
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
