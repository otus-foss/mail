package com.otus.mail.feature.inbox.domain

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
            )
        assertEquals(layout, SidebarLayout.decode(SidebarLayout.encode(layout)))
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
