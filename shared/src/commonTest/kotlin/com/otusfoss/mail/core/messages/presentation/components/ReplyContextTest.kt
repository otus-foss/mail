package com.otusfoss.mail.core.messages.presentation.components

import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyContextTest {
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val alan = MailSender("Alan Turing", "alan@example.org")
    private val alias = MailSender("", "ada@ruu.by")
    private val own = OwnAddresses(mapOf("work" to listOf("accounts@ruu.by", "ada@ruu.by")))

    private fun message(to: List<MailSender>) =
        MailMessage(
            "work/e1",
            grace,
            "Plans",
            "",
            ReceivedAt("2026-10-12", "09:14"),
            MailLocation("Inbox", "Work", "work", "accounts@ruu.by"),
            to = to,
        )

    @Test
    fun replyAllLeavesOutAnAddressThatIsNotTheSignIn() =
        assertEquals("2 people", replyContext(message(listOf(alias, alan)), MailAction.ReplyAll, own)?.subtitle)

    @Test
    fun followUpLeavesOutTheUsersOtherAddresses() =
        assertEquals("Alan Turing", replyContext(message(listOf(alias, alan)), MailAction.FollowUp, own)?.subtitle)
}
