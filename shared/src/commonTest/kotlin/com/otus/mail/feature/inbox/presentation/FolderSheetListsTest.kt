package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.feature.inbox.domain.ThreadMember
import com.otus.mail.feature.inbox.domain.memberMailboxes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderSheetListsTest {
    private val inbox = MessageFolder("in", listOf("Inbox"), role = "inbox")
    private val spam = MessageFolder("sp", listOf("Junk"), role = "junk")
    private val trash = MessageFolder("tr", listOf("Trash"), role = "trash")
    private val readLater = MessageFolder("rl", listOf("Read later"), readLater = true)
    private val clients = MessageFolder("cl", listOf("Clients"), color = "blue")
    private val invoices = MessageFolder("fi", listOf("Finance", "Invoices"), color = "yellow")
    private val taxes = MessageFolder("ft", listOf("Finance", "Taxes"), color = "yellow")
    private val all = listOf(taxes, trash, invoices, spam, readLater, clients, inbox)
    private val ada = MailSender("Ada Lovelace", "ada@lovelace.example")

    private fun message(
        mailboxIds: Set<String>,
        conversation: MailConversation? = null,
    ) = MailMessage(
        "m1",
        ada,
        "Invoice October",
        "",
        ReceivedAt("2026-10-18", "09:14"),
        MailLocation("Clients", "Ada", mailboxId = "cl", mailboxIds = mailboxIds),
        conversation = conversation,
    )

    @Test
    fun theFoldersTheMailIsInComeFirstAndTheRestFollowByPath() {
        val lists = folderSheetLists(setOf("cl", "fi"), all)
        assertEquals(listOf(clients, invoices), lists.current)
        assertEquals(listOf(inbox, taxes), lists.others)
    }

    @Test
    fun spamAndTrashAreNeverOfferedButShowWhileTheMailIsInThem() {
        val lists = folderSheetLists(setOf("tr"), all)
        assertEquals(listOf(trash), lists.current)
        assertFalse(spam in lists.others)
    }

    @Test
    fun readLaterStaysWithTheBookmark() {
        val lists = folderSheetLists(setOf("cl", "rl"), all)
        assertFalse(readLater in lists.current + lists.others)
    }

    @Test
    fun aConversationIsInEveryFolderOfItsEmails() {
        val conversation =
            MailConversation(
                threadId = "t1",
                messageCount = 2,
                last = ThreadEmail(ada, emptyList(), ReceivedAt("2026-10-18", "09:14")),
                senders = listOf(ada),
                answered = false,
                members = listOf(ThreadMember("e1", setOf("cl")), ThreadMember("e2", setOf("in", "fi"))),
            )
        val mailboxes = message(setOf("cl"), conversation).memberMailboxes
        assertEquals(mapOf("e1" to setOf("cl"), "e2" to setOf("in", "fi")), mailboxes)
        assertEquals(
            listOf(inbox, clients, invoices),
            folderSheetLists(mailboxes.values.flatten().toSet(), all).current,
        )
    }

    @Test
    fun searchMatchesAnyPartOfThePath() {
        assertTrue(invoices.matchesSearch("finance"))
        assertTrue(invoices.matchesSearch(" voic "))
        assertFalse(clients.matchesSearch("finance"))
    }
}
