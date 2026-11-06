package com.otus.mail.app

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.settings.domain.PinSize
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarMailboxTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox),
                MailFolder("ada", "s", "Sent", role = MailboxRole.Sent),
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
                SidebarPin.Role(MailboxRole.Sent),
                SidebarPin.Folder(listOf("gone")),
            )
        val layout = SidebarLayout(pinned = pinned, sizes = mapOf(SidebarPin.Role(MailboxRole.Sent) to PinSize.Wide))
        val mailboxes = sidebarMailboxes(listOf(ada), layout = layout)
        assertEquals(listOf("Inbox", "Clients", "Sent"), mailboxes.map { it.title })
        assertEquals(MailPage.Shared(listOf("work", "clients")), mailboxes[1].page)
        assertEquals(listOf(PinSize.Full, PinSize.Small, PinSize.Wide), mailboxes.map { it.size })
    }

    @Test
    fun anUnpinnedInboxLeavesTheSidebar() {
        val mailboxes =
            sidebarMailboxes(listOf(ada), layout = SidebarLayout(pinned = listOf(SidebarPin.Role(MailboxRole.Sent))))
        assertEquals(listOf("Sent"), mailboxes.map { it.title })
    }

    @Test
    fun everyFolderCanBePinnedAfterTheInboxConversationsAndTheSystemFolders() {
        assertEquals(
            listOf(
                SidebarPin.Inbox,
                SidebarPin.Conversations,
                SidebarPin.Contacts,
                SidebarPin.Role(MailboxRole.Sent),
                SidebarPin.Folder(listOf("work")),
                SidebarPin.Folder(listOf("work", "clients")),
            ),
            pinnableMailboxes(listOf(ada)).map { it.first },
        )
    }
}
