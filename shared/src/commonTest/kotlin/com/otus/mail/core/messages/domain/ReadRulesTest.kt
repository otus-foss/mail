package com.otus.mail.core.messages.domain

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadRulesTest {
    private val known = setOf(EmailAddressKey("grace@example.org"))
    private val allowed = listOf("News@Sentry.io")

    private fun shows(
        setting: RemoteImages,
        sender: String,
        inSpam: Boolean = false,
    ) = showsRemoteImages(setting, sender, known, allowed, inSpam)

    @Test
    fun spamNeverLoadsImages() {
        assertFalse(shows(RemoteImages.Always, "grace@example.org", inSpam = true))
        assertFalse(shows(RemoteImages.Known, "grace@example.org", inSpam = true))
    }

    @Test
    fun alwaysLoadsFromAnyone() = assertTrue(shows(RemoteImages.Always, "stranger@example.com"))

    @Test
    fun neverLoadsEvenFromKnownPeople() = assertFalse(shows(RemoteImages.Never, "grace@example.org"))

    @Test
    fun knownLoadsFromKnownPeopleIgnoringCase() = assertTrue(shows(RemoteImages.Known, "Grace@Example.org"))

    @Test
    fun knownLoadsFromAllowedSenders() = assertTrue(shows(RemoteImages.Known, "news@sentry.io"))

    @Test
    fun knownBlocksUnknownSenders() = assertFalse(shows(RemoteImages.Known, "stranger@example.com"))

    private val ada = MailSender("Ada Lovelace", "ada@example.org")
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val alan = MailSender("Alan Turing", "alan@example.org")
    private val own = OwnAddresses(mapOf("work" to listOf("ada@example.org")))

    private fun message(
        sender: MailSender = grace,
        to: List<MailSender> = listOf(ada),
        cc: List<MailSender> = emptyList(),
    ) = MailMessage(
        "work/e1",
        sender,
        "Plans",
        "",
        ReceivedAt("2026-10-12", "09:14"),
        MailLocation("Inbox", "Work", "work", "ada@example.org", mailboxRole = MailboxRole.Inbox),
        to = to,
        cc = cc,
    )

    @Test
    fun onePersonGetsReply() {
        assertEquals(MailAction.Reply, message().replyAction(own))
        assertNull(message().otherReply(own))
    }

    @Test
    fun severalPeopleGetReplyAll() {
        val message = message(to = listOf(ada, alan))
        assertEquals(MailAction.ReplyAll, message.replyAction(own))
        assertEquals(MailAction.Reply, message.otherReply(own))
    }

    @Test
    fun ccCountsToo() = assertEquals(MailAction.ReplyAll, message(cc = listOf(alan)).replyAction(own))

    @Test
    fun ownAddressesDoNotCount() {
        val alias = MailSender("Ada", "ADA@example.org")
        assertEquals(MailAction.Reply, message(to = listOf(ada, alias)).replyAction(own))
    }

    @Test
    fun duplicatesCountOnce() {
        val again = MailSender("Grace", "Grace@Example.org")
        assertEquals(MailAction.Reply, message(to = listOf(ada, again), cc = listOf(grace)).replyAction(own))
    }

    @Test
    fun theUsersOwnMessageCountsItsRecipients() {
        assertEquals(MailAction.Reply, message(sender = ada, to = listOf(grace)).replyAction(own))
        assertEquals(MailAction.ReplyAll, message(sender = ada, to = listOf(grace, alan)).replyAction(own))
    }
}
