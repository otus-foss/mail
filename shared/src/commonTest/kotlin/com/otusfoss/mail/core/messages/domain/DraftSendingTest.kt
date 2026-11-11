package com.otusfoss.mail.core.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftSendingTest {
    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Lunch",
            from = MailSender("Ada", "ada@example.com"),
            to = emptyList(),
            cc = emptyList(),
            sentLabel = "2026-10-16 12:00",
            body = "",
        )

    @Test
    fun repliesAndForwardsAnswerTheirOriginal() {
        assertEquals(
            AnsweredMessage("m1", forwarded = false),
            ComposeRequest.Reply(original, all = true).answered("a1"),
        )
        assertEquals(AnsweredMessage("m1", forwarded = true), ComposeRequest.Forward(original).answered("a1"))
    }

    @Test
    fun newMessagesFollowUpsAndOtherAccountsAnswerNothing() {
        assertNull(ComposeRequest.New("a1").answered("a1"))
        assertNull(ComposeRequest.FollowUp(original).answered("a1"))
        assertNull(ComposeRequest.Reply(original, all = false).answered("a2"))
    }
}
