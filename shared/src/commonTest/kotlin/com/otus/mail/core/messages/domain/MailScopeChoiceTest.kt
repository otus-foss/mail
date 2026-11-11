package com.otus.mail.core.messages.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.settings.domain.SidebarCount
import kotlin.test.Test
import kotlin.test.assertEquals

class MailScopeChoiceTest {
    private fun message(
        id: String,
        accountId: String,
        to: List<String> = emptyList(),
        cc: List<String> = emptyList(),
        mailbox: String = MailboxRole.Inbox.title,
        unread: Boolean = false,
        from: String = "grace@example.org",
    ) = MailMessage(
        id,
        MailSender("Grace", from),
        "Hello",
        "",
        ReceivedAt("2026-10-01", "09:00"),
        MailLocation(mailbox, accountId, mailboxRole = MailboxRole.Inbox.takeIf { mailbox == it.title }),
        MailAttributes(unread = unread),
        to = to.map { MailSender("", it) },
        cc = cc.map { MailSender("", it) },
    )

    private val messages =
        listOf(
            message("toHi", "design", to = listOf("Hi@Ruu.by")),
            message("ccTest", "design", cc = listOf("test@ruu.by")),
            message("company", "company", to = listOf("hi@ruu.by")),
            message("sent", "design", to = listOf("grace@example.org"), mailbox = "sent", from = "hi@ruu.by"),
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
            messages.within(MailScopeChoice.Address("design", "hi@ruu.by")).map { it.id },
        )
        assertEquals(
            listOf("ccTest"),
            messages.within(MailScopeChoice.Address("design", "TEST@ruu.by")).map { it.id },
        )
    }

    @Test
    fun aRemovedAccountsChoiceFallsBackToAllMail() {
        val address = MailScopeChoice.Address("design", "hi@ruu.by")
        assertEquals(address, address.within(listOf("design")))
        assertEquals(MailScopeChoice.All, address.within(listOf("company")))
        assertEquals(MailScopeChoice.All, MailScopeChoice.Account("gone").within(emptyList()))
    }

    @Test
    fun anAddressCountsItsInboxMailByTheCountsSetting() {
        val inbox =
            listOf(
                message("a", "design", to = listOf("hi@ruu.by"), unread = true),
                message("b", "design", to = listOf("hi@ruu.by")),
                message("archived", "design", to = listOf("hi@ruu.by"), mailbox = "Archive", unread = true),
                message("other", "design", to = listOf("test@ruu.by"), unread = true),
            )
        val address = MailScopeChoice.Address("design", "hi@ruu.by")
        assertEquals(1L, addressCount(inbox, address, SidebarCount.Unread))
        assertEquals(2L, addressCount(inbox, address, SidebarCount.Total))
        assertEquals(null, addressCount(inbox, address, SidebarCount.None))
        assertEquals(null, addressCount(inbox, MailScopeChoice.Address("design", "none@ruu.by"), SidebarCount.Total))
    }
}
