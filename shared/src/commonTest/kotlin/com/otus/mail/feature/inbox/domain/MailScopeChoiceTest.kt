package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MailScopeChoiceTest {
    private fun message(
        id: String,
        accountId: String,
        to: List<String> = emptyList(),
        cc: List<String> = emptyList(),
        mailbox: String = INBOX_MAILBOX,
        unread: Boolean = false,
        from: String = "grace@example.org",
    ) = MailMessage(
        id,
        MailSender("Grace", from),
        "Hello",
        "",
        ReceivedAt("2026-10-01", "09:00"),
        MailLocation(mailbox, accountId),
        MailAttributes(unread = unread),
        to = to.map { MailSender("", it) },
        cc = cc.map { MailSender("", it) },
    )

    private val messages =
        listOf(
            message("toHi", "design", to = listOf("Hi@Design.ch")),
            message("ccTest", "design", cc = listOf("test@design.ch")),
            message("company", "company", to = listOf("hi@design.ch")),
            message("sent", "design", to = listOf("grace@example.org"), mailbox = "sent", from = "hi@design.ch"),
        )

    @Test
    fun allMailShowsEveryMessage() {
        assertEquals(messages, messages.within(MailScopeChoice.All))
    }

    @Test
    fun anAccountShowsOnlyItsOwnMail() {
        assertEquals(listOf("toHi", "ccTest", "sent"), messages.within(MailScopeChoice.Account("design")).map { it.id })
    }

    @Test
    fun anAddressShowsItsAccountsMailToOrFromItIgnoringCase() {
        assertEquals(
            listOf("toHi", "sent"),
            messages.within(MailScopeChoice.Address("design", "hi@design.ch")).map { it.id },
        )
        assertEquals(
            listOf("ccTest"),
            messages.within(MailScopeChoice.Address("design", "TEST@design.ch")).map { it.id },
        )
    }

    @Test
    fun aRemovedAccountsChoiceFallsBackToAllMail() {
        val address = MailScopeChoice.Address("design", "hi@design.ch")
        assertEquals(address, address.within(listOf("design")))
        assertEquals(MailScopeChoice.All, address.within(listOf("company")))
        assertEquals(MailScopeChoice.All, MailScopeChoice.Account("gone").within(emptyList()))
    }

    @Test
    fun anAddressCountsItsInboxMailByTheCountsSetting() {
        val inbox =
            listOf(
                message("a", "design", to = listOf("hi@design.ch"), unread = true),
                message("b", "design", to = listOf("hi@design.ch")),
                message("archived", "design", to = listOf("hi@design.ch"), mailbox = "Archive", unread = true),
                message("other", "design", to = listOf("test@design.ch"), unread = true),
            )
        val address = MailScopeChoice.Address("design", "hi@design.ch")
        assertEquals(1L, addressCount(inbox, address, SidebarCount.Unread))
        assertEquals(2L, addressCount(inbox, address, SidebarCount.Total))
        assertEquals(null, addressCount(inbox, address, SidebarCount.None))
        assertEquals(null, addressCount(inbox, MailScopeChoice.Address("design", "none@design.ch"), SidebarCount.Total))
    }
}
