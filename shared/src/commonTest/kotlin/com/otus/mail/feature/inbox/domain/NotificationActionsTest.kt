package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationActionsTest {
    @Test
    fun choicesSurviveSavingAndAnEmptyChoiceStaysEmpty() {
        val chosen = listOf(MailAction.Spam, MailAction.Reply)
        assertEquals("spam,reply", encodeNotificationActions(chosen))
        assertEquals(chosen, decodeNotificationActions(encodeNotificationActions(chosen)))
        assertEquals(emptyList(), decodeNotificationActions(encodeNotificationActions(emptyList())))
        assertEquals(
            listOf(MailAction.Flag, MailAction.Trash, MailAction.Archive),
            decodeNotificationActions("flag,move,trash,flag,archive,read"),
        )
    }
}
