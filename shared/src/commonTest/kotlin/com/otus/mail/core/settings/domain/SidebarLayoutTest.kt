package com.otus.mail.core.settings.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarLayoutTest {
    @Test
    fun aLayoutSurvivesEncoding() {
        val layout =
            SidebarLayout(
                sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes),
                hidden = setOf(SidebarSection.Mailboxes),
                mailboxes = MailboxStyle.Rows,
                counts = SidebarCount.None,
                folderColors = false,
                sizes = mapOf(SidebarPin.Inbox to PinSize.Small, SidebarPin.Folder(listOf("a,b")) to PinSize.Wide),
            )
        assertEquals(layout, SidebarLayout.decode(SidebarLayout.encode(layout)))
    }

    @Test
    fun theInboxSpansTheGridAndOtherPinsOneColumnUntilResized() {
        val layout = SidebarLayout(sizes = mapOf(SidebarPin.Contacts to PinSize.Wide))
        assertEquals(PinSize.Full, layout.sizeOf(SidebarPin.Inbox))
        assertEquals(PinSize.Small, layout.sizeOf(SidebarPin.ReadLater))
        assertEquals(PinSize.Wide, layout.sizeOf(SidebarPin.Contacts))
        assertEquals(emptyMap(), SidebarLayout.decode("sizes=huge:inbox,wide:future").sizes)
    }

    @Test
    fun folderColorsStayOnUntilTurnedOff() {
        assertEquals(true, SidebarLayout.decode("counts=total").folderColors)
        assertEquals(true, SidebarLayout.decode("folderColors=maybe").folderColors)
        assertEquals(false, SidebarLayout.decode("folderColors=false").folderColors)
    }

    @Test
    fun unknownValuesReadAsDefaultsAndMissingSectionsGoLast() {
        assertEquals(
            SidebarLayout(sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes)),
            SidebarLayout.decode("sections=labels,folders\nhidden=labels\nmailboxes=grid\nfuture=1"),
        )
        assertEquals(SidebarLayout(), SidebarLayout.decode(""))
    }

    @Test
    fun aLayoutSavedBeforeContactsTheInboxAndScheduledPinsThemOnce() {
        val saved = SidebarLayout.decode("pinned=read_later,role:sent")
        val scheduled = SidebarPin.Role(MailboxRole.Scheduled)
        assertEquals(
            listOf(
                SidebarPin.Inbox,
                SidebarPin.ReadLater,
                SidebarPin.Role(MailboxRole.Sent),
                SidebarPin.Contacts,
                scheduled,
            ),
            saved.pinned,
        )
        val unpinned = saved.copy(pinned = saved.pinned - SidebarPin.Contacts - SidebarPin.Inbox - scheduled)
        assertEquals(unpinned, SidebarLayout.decode(SidebarLayout.encode(unpinned)))
    }

    @Test
    fun reorderingTheShownPinsKeepsThoseThatCannotShowAfterThem() {
        val gone = SidebarPin.Folder(listOf("gone"))
        val layout = SidebarLayout(pinned = listOf(SidebarPin.Inbox, gone, SidebarPin.Contacts))
        assertEquals(
            listOf(SidebarPin.Contacts, SidebarPin.Inbox, gone),
            layout.withPinOrder(listOf(SidebarPin.Contacts, SidebarPin.Inbox)).pinned,
        )
    }

    @Test
    fun movingAndHidingASectionKeepsTheRest() {
        val layout = SidebarLayout().moved(from = 1, to = 0).withShown(SidebarSection.Mailboxes, shown = false)
        assertEquals(listOf(SidebarSection.Folders, SidebarSection.Mailboxes), layout.sections)
        assertEquals(listOf(SidebarSection.Folders), layout.shown)
        assertEquals(
            SidebarLayout().sections,
            layout.withShown(SidebarSection.Mailboxes, shown = true).moved(0, 1).shown,
        )
    }

    @Test
    fun theLastShownSectionStaysOn() {
        val layout = SidebarLayout().withShown(SidebarSection.Mailboxes, shown = false)
        assertEquals(layout, layout.withShown(SidebarSection.Folders, shown = false))
    }
}
