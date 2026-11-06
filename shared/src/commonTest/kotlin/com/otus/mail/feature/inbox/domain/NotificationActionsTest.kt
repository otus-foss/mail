package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationActionsTest {
    private val everything =
        AlertAccount(setOf(MailboxRole.Inbox, MailboxRole.Archive, MailboxRole.Trash, MailboxRole.Junk), canSend = true)

    @Test
    fun alertsOfferTrashArchiveAndMarkReadUntilTheUserChooses() {
        val buttons = MailBehavior().notificationActions.alertButtons(everything)
        assertEquals(listOf(MailAction.Trash, MailAction.Archive, MailAction.Read), buttons)
    }

    @Test
    fun theChosenOrderIsKept() {
        val chosen = listOf(MailAction.Reply, MailAction.Flag, MailAction.Spam)
        assertEquals(chosen, chosen.alertButtons(everything))
    }

    @Test
    fun atMostThreeButtonsShow() {
        val chosen = listOf(MailAction.Flag, MailAction.Read, MailAction.Spam, MailAction.Trash)
        assertEquals(chosen.take(3), chosen.alertButtons(everything))
    }

    @Test
    fun actionsTheAccountCannotRunAreLeftOutAndTheRestKeepTheirOrder() {
        val inboxOnly = AlertAccount(setOf(MailboxRole.Inbox), canSend = false)
        val chosen = listOf(MailAction.Archive, MailAction.Flag, MailAction.Trash, MailAction.Reply, MailAction.Read)
        assertEquals(listOf(MailAction.Flag, MailAction.Read), chosen.alertButtons(inboxOnly))
        val noSpam = AlertAccount(setOf(MailboxRole.Inbox, MailboxRole.Trash), canSend = true)
        assertEquals(
            listOf(MailAction.Trash, MailAction.Reply),
            listOf(MailAction.Spam, MailAction.Trash, MailAction.Reply).alertButtons(noSpam),
        )
    }

    @Test
    fun noChoiceShowsNoButtons() {
        assertEquals(emptyList(), emptyList<MailAction>().alertButtons(everything))
    }

    @Test
    fun actionsAlertsCannotRunAndRepeatsAreLeftOut() {
        val chosen = listOf(MailAction.Move, MailAction.Read, MailAction.Read, MailAction.ReplyAll, MailAction.Flag)
        assertEquals(listOf(MailAction.Read, MailAction.Flag), chosen.alertButtons(everything))
    }

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
