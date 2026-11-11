package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBadgeTest {
    private val own = OwnAddresses(mapOf("ada" to listOf("ada@home.test"), "work" to listOf("ada@work.test")))

    private fun message(
        id: String,
        login: String,
        address: String,
    ) = MailMessage(
        id,
        MailSender("Grace", "grace@example.org"),
        "Hi",
        "",
        ReceivedAt("2026-10-27", "11:00"),
        MailLocation("Inbox", login, accountAddress = address, mailboxRole = MailboxRole.Inbox),
    )

    private val home = message("m1", "ada", "ada@home.test")
    private val work = message("m2", "work", "ada@work.test")

    @Test
    fun rowsOfSeveralAddressesMarkThem() {
        assertTrue(mixesAddresses(listOf(home, work), own))
        assertFalse(mixesAddresses(listOf(home), own))
    }
}
