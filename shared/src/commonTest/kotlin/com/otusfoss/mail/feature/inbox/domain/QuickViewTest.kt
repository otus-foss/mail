package com.otusfoss.mail.feature.inbox.domain

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailAttributes
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickViewTest {
    private val me = MailSender("Ada", "ada@example.org")
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val alan = MailSender("Alan Turing", "alan@example.org")
    private val news = MailSender("Weekly", "news@example.org")
    private val own = OwnAddresses(mapOf("work" to listOf("ada@example.org")))

    private fun message(
        sender: MailSender,
        to: List<MailSender> = listOf(me),
        unread: Boolean = false,
    ) = MailMessage(
        "work/${sender.address}",
        sender,
        "Plans",
        "",
        ReceivedAt("2026-10-04", "09:30"),
        MailLocation("Inbox", "Work", "work", mailboxRole = MailboxRole.Inbox),
        MailAttributes(unread = unread),
        to = to,
    )

    @Test
    fun modulesSurviveSavingAndKeepAtMostTwo() {
        val modules = listOf(QuickViewModule.RecentFiles, QuickViewModule.People)
        assertEquals(modules, QuickViewModule.decode(QuickViewModule.encode(modules)))
        assertEquals("recent_files,people", QuickViewModule.encode(modules))
        assertEquals(
            listOf(QuickViewModule.Counts, QuickViewModule.People),
            QuickViewModule.decode("counts,later,people,addresses"),
        )
        assertEquals(emptyList(), QuickViewModule.decode(""))
    }

    @Test
    fun theDefaultShowsCountsAndPeople() {
        assertEquals(listOf(QuickViewModule.Counts, QuickViewModule.People), MailBehavior().list.quickView)
    }

    @Test
    fun peopleTheUserWritesToComeFirstAndSendersTheyNeverAnswerStayOut() {
        val messages =
            listOf(
                message(me, to = listOf(alan)),
                message(me, to = listOf(grace)),
                message(me, to = listOf(grace)),
                message(grace, unread = true),
                message(news),
                message(news),
                message(news),
            )
        val people = frequentSenders(messages, own)
        assertEquals(listOf(grace.address, alan.address), people.map { it.person.address })
        assertTrue(people.first().unread)
        assertFalse(people.last().unread)
    }

    @Test
    fun withoutSentMailEverySenderCountsAndTheUserNever() {
        val messages = listOf(message(news), message(news), message(grace), message(me, to = emptyList()))
        assertEquals(listOf(news.address, grace.address), frequentSenders(messages, own).map { it.person.address })
    }

    @Test
    fun peopleStopAtTheLimit() {
        val senders = (1..7).map { MailSender("P$it", "p$it@example.org") }
        assertEquals(5, frequentSenders(senders.map { message(it) }, own).size)
    }
}
