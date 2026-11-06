package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.settings.domain.SidebarCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidebarCounterTest {
    private val folders =
        listOf(
            MailFolder("ada", "i", "Inbox", unread = 2, total = 10),
            MailFolder("grace", "i", "Inbox", unread = 3, total = 4),
        )

    @Test
    fun everyAccountAddsUpAndOneAccountCountsOnlyItsOwn() {
        assertEquals("5", SidebarCounter(null, SidebarCount.Unread).of(folders))
        assertEquals("3", SidebarCounter("grace", SidebarCount.Unread).of(folders))
        assertEquals("10", SidebarCounter("ada", SidebarCount.Total).of(folders))
    }

    @Test
    fun nothingToCountOrCountsOffShowNoCount() {
        assertNull(SidebarCounter("nobody", SidebarCount.Unread).of(folders))
        assertNull(SidebarCounter(null, SidebarCount.None).of(folders))
    }

    @Test
    fun aBadgeCapsLargeCounts() {
        val many = listOf(MailFolder("ada", "i", "Inbox", unread = 1_000))
        assertEquals("999+", SidebarCounter(null, SidebarCount.Unread).badge(many))
        assertEquals("5", SidebarCounter(null, SidebarCount.Unread).badge(folders))
    }
}
