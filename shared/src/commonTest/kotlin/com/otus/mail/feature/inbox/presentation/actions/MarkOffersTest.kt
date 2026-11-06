package com.otus.mail.feature.inbox.presentation.actions

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Marks for several messages reach the app as one change, so it can save them together. */
class MarkOffersTest {
    private val marked = mutableListOf<Triple<List<String>, MessageFlag, Boolean>>()

    private val actions =
        offerActions(
            canMark = { message, _ -> message.emailId.isNotEmpty() },
            onMark = { messages, flag, on -> marked += Triple(messages.map { it.id }, flag, on) },
        )

    private fun list(vararg results: MailMessage) =
        ActionSubject(actions, list = ListSubject(results.toList(), null, onSearch = {}, onFilters = {}))

    @Test
    fun markAllReadMarksEveryUnreadMessageItMayChangeAtOnce() {
        val supplied = offerMessage("4", unread = true).copy(emailId = "")
        val offered =
            assertNotNull(
                list(offerMessage("1", unread = true), offerMessage("2"), offerMessage("3", unread = true), supplied)
                    .offer(MailAction.MarkAllRead),
            )
        offered.run()
        assertEquals(listOf(Triple(listOf("1", "3"), MessageFlag.Read, true)), marked)
        assertNull(list(offerMessage("2")).offer(MailAction.MarkAllRead), "Nothing is unread")
    }

    @Test
    fun aSelectionIsMarkedAtOnce() {
        val selection = listOf(offerMessage("1", unread = true), offerMessage("2"))
        assertNotNull(ActionSubject(actions, selection).offer(MailAction.Read)).run()
        assertEquals(listOf(Triple(listOf("1", "2"), MessageFlag.Read, true)), marked)
    }

    @Test
    fun openingAnUnreadMessageMarksOnlyThatMessage() {
        actions.opened(offerMessage("1", unread = true))
        actions.opened(offerMessage("2"))
        assertEquals(listOf(Triple(listOf("1"), MessageFlag.Read, true)), marked)
    }
}
