package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarPinTest {
    @Test
    fun pinsSurviveEncodingEvenWithFolderNamesThatHoldSeparators() {
        val pins =
            listOf(
                SidebarPin.Folder(listOf("clients, 2026", "a/b 100%")),
                SidebarPin.Role("sent"),
                SidebarPin.ReadLater,
                SidebarPin.Conversations,
            )
        val layout = SidebarLayout(pinned = pins)
        assertEquals(layout, SidebarLayout.decode(SidebarLayout.encode(layout)))
    }

    @Test
    fun aLayoutSavedBeforePinsPinsTheOldMailboxesAndAnEmptyListStaysEmpty() {
        assertEquals(SidebarPin.defaults, SidebarLayout.decode("mailboxes=rows").pinned)
        assertEquals(emptyList(), SidebarLayout.decode("pinned=\noffered=contacts").pinned)
        assertEquals(
            listOf(SidebarPin.ReadLater),
            SidebarLayout.decode("pinned=future:1,read_later,role:\noffered=contacts").pinned,
        )
    }
}
