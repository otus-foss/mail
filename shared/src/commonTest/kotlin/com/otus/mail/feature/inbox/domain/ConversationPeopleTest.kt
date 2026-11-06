package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationPeopleTest {
    private val ada = MailSender("Ada", "ada@example.org")
    private val alias = MailSender("Ada", "Lovelace@Example.org")
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val radia = MailSender("", "radia@example.org")
    private val own = OwnAddresses(mapOf("work" to listOf("ada@example.org", "lovelace@example.org")))

    private fun message(
        sender: MailSender = grace,
        to: List<MailSender> = listOf(ada),
        cc: List<MailSender> = emptyList(),
        conversation: MailConversation? = null,
        accountAddress: String = "",
    ) = MailMessage(
        "work/e1",
        sender,
        "Plans",
        "",
        ReceivedAt("2026-10-04", "09:30"),
        MailLocation("Inbox", "Work", "work", accountAddress, mailboxRole = MailboxRole.Inbox),
        to = to,
        cc = cc,
        conversation = conversation,
    )

    private fun thread(
        last: ThreadEmail,
        senders: List<MailSender>,
    ) = MailConversation("t1", 3, last, senders, answered = false, members = emptyList())

    private val received = ReceivedAt("2026-10-05", "10:00")

    @Test
    fun ownAddressesMatchWithoutCaseAndOnlyForTheirLogin() {
        assertTrue(own.isMine("work", "ADA@example.org "))
        assertFalse(own.isMine("home", "ada@example.org"))
        assertFalse(own.isMine("work", "grace@example.org"))
    }

    @Test
    fun aThreadNamesItsNewestSenderAndCountsOthersWithoutTheUser() {
        val line =
            message(conversation = thread(ThreadEmail(grace, listOf(ada), received), listOf(grace, ada, radia, alias)))
                .senderLine(own)
        assertEquals(SenderLine.From("Grace Hopper", 1), line)
    }

    @Test
    fun theUsersOwnNewestEmailNamesItsRecipient() {
        val last = ThreadEmail(alias, listOf(ada, grace, radia), received)
        val line = message(conversation = thread(last, listOf(alias, ada, grace, radia))).senderLine(own)
        assertEquals(SenderLine.YouTo("Grace Hopper", 1), line, "The first recipient who is not the user")
    }

    @Test
    fun recipientsWhoNeverWroteDoNotCount() {
        val team = (1..10).map { MailSender("", "student$it@example.org") }
        val last = ThreadEmail(grace, team + ada, received)
        val line = message(conversation = thread(last, listOf(grace, ada, radia))).senderLine(own)
        assertEquals(SenderLine.From("Grace Hopper", 1), line, "Only Radia, who wrote, counts besides Grace")
    }

    @Test
    fun aNoteToSelfNamesYourself() {
        val last = ThreadEmail(ada, listOf(ada), received)
        assertEquals(SenderLine.YouTo(SELF_NAME, 0), message(conversation = thread(last, listOf(ada))).senderLine(own))
    }

    @Test
    fun aMessageWithoutConversationUsesItsOwnPeople() {
        assertEquals(
            SenderLine.From("Grace Hopper", 0),
            message(cc = listOf(radia)).senderLine(own),
            "Radia never wrote",
        )
        val sent = message(sender = ada, to = listOf(radia), cc = listOf(grace))
        assertEquals(SenderLine.YouTo("radia@example.org", 1), sent.senderLine(own), "A blank name shows the address")
    }

    @Test
    fun theSignInAddressCountsAsOwnBeforeIdentitiesLoad() {
        val line = message(sender = ada, to = listOf(grace), accountAddress = "ada@example.org")
        assertEquals(SenderLine.YouTo("Grace Hopper", 0), line.senderLine(OwnAddresses(emptyMap())))
    }

    @Test
    fun receivedOnIsTheOwnRecipientOrTheSignInAddress() {
        assertEquals("Lovelace@Example.org", message(to = listOf(grace), cc = listOf(alias)).receivedOn(own))
        val list = message(to = listOf(MailSender("Team", "team@example.org")), accountAddress = "ada@example.org")
        assertEquals("ada@example.org", list.receivedOn(own), "Mail to a list falls back to the account")
        assertNull(message(to = listOf(grace)).receivedOn(own))
    }

    @Test
    fun threadSubjectsLoseReplyAndForwardPrefixes() {
        assertEquals("Plans", threadSubject("Re: Plans"))
        assertEquals("Plans", threadSubject("RE: Re: Fwd: Plans"))
        assertEquals("Plans", threadSubject("Re[2]: Plans"))
        assertEquals("Plans", threadSubject("AW: WG: Antw: Plans "))
        assertEquals("Plans", threadSubject("FW:Plans"))
        assertEquals("Reply: Plans", threadSubject("Reply: Plans"), "Only whole prefixes go")
        assertEquals("Plans: Re: notes", threadSubject("Plans: Re: notes"))
    }

    @Test
    fun withContentKeepsThePreviewWhenNoTextLoaded() {
        val listed = message().copy(body = "Short", preview = "Short")
        assertEquals("Short", listed.withContent(null).body)
        assertEquals("Short", listed.withContent(MessageContent("", truncated = true, html = null)).body)
        val full = listed.withContent(MessageContent("Short, and the rest", truncated = false, html = null))
        assertEquals("Short, and the rest", full.body)
        assertEquals("Short", full.preview)
    }
}
