package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageListingTest {
    private fun row(
        id: String,
        login: String,
        emailId: String,
        members: List<String> = emptyList(),
    ) = MailMessage(
        id,
        GRACE,
        "Order",
        "",
        ReceivedAt("2026-10-04", "09:30"),
        MailLocation("Inbox", login, login, mailboxRole = MailboxRole.Inbox),
        emailId = emailId,
        conversation = members.takeIf { it.isNotEmpty() }?.let(::thread),
    )

    private fun thread(members: List<String>) =
        MailConversation(
            threadId = "t-${members.first()}",
            messageCount = members.size,
            last = ThreadEmail(GRACE, emptyList(), ReceivedAt("2026-10-04", "09:30")),
            senders = listOf(GRACE),
            answered = false,
            members = members.map { ThreadMember(it, setOf("inbox")) },
        )

    private val rows =
        listOf(
            row("ada/m1", "ada", "m1"),
            row("ada/m3", "ada", "m3", members = listOf("m3", "m2")),
            row("bob/m1", "bob", "m1"),
        )

    @Test
    fun theRowOfTheEmailInItsLoginIsFound() {
        assertEquals("bob/m1", rows.listing("bob", "m1")?.id)
        assertEquals("ada/m1", rows.listing("ada", "m1")?.id)
    }

    @Test
    fun anEmailInsideAConversationFindsTheConversation() {
        assertEquals("ada/m3", rows.listing("ada", "m2")?.id)
    }

    @Test
    fun aMissingEmailOrLoginFindsNothing() {
        assertNull(rows.listing("ada", "m9"))
        assertNull(rows.listing("cy", "m1"))
    }

    private companion object {
        val GRACE = MailSender("Grace", "grace@example.org")
    }
}
