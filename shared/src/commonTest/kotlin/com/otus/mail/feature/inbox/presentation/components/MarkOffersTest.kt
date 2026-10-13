package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMoves
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Marks for several messages reach the app as one change, so it can save them together. */
class MarkOffersTest {
    private val marked = mutableListOf<Triple<List<String>, MessageFlag, Boolean>>()

    private val actions =
        MessageActions(
            moves =
                MessageMoves(
                    targets = { emptyList() },
                    onMove = { _, _ -> },
                    suggested = { null },
                    futureSorting = { null },
                ),
            canMark = { message, _ -> message.emailId.isNotEmpty() },
            onMark = { messages, flag, on -> marked += Triple(messages.map { it.id }, flag, on) },
            ruleForSender = { null },
            readLater = { null },
            behavior = { MailBehavior() },
        )

    private fun list(vararg results: MailMessage) =
        ListActions(results.toList(), null, actions, onSearch = {}, onFilters = {}, onCompose = {})

    @Test
    fun markAllReadMarksEveryUnreadMessageItMayChangeAtOnce() {
        val supplied = message("4", unread = true).copy(emailId = "")
        val offered =
            assertNotNull(
                list(message("1", unread = true), message("2", unread = false), message("3", unread = true), supplied)
                    .offer(MailAction.MarkAllRead),
            )
        offered.run()
        assertEquals(listOf(Triple(listOf("1", "3"), MessageFlag.Read, true)), marked)
        assertNull(list(message("2", unread = false)).offer(MailAction.MarkAllRead), "Nothing is unread")
    }

    @Test
    fun aSelectionIsMarkedAtOnce() {
        val selection = listOf(message("1", unread = true), message("2", unread = false))
        assertNotNull(actions.offer(selection, MailAction.Read, chooseFolder = { _, _ -> })).run()
        assertEquals(listOf(Triple(listOf("1", "2"), MessageFlag.Read, true)), marked)
    }

    @Test
    fun openingAnUnreadMessageMarksOnlyThatMessage() {
        actions.opened(message("1", unread = true))
        actions.opened(message("2", unread = false))
        assertEquals(listOf(Triple(listOf("1"), MessageFlag.Read, true)), marked)
    }

    private fun message(
        id: String,
        unread: Boolean,
    ) = MailMessage(
        id,
        MailSender("Ada", "ada@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Inbox", "Ada", "ada", mailboxId = "inbox"),
        MailAttributes(unread = unread),
        emailId = "m$id",
    )
}
