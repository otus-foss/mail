package com.otusfoss.mail.feature.read.presentation

import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which sections the ⋯ menu has and what it leaves out, by page. */
class ReadMenuTest {
    private val me = MailSender("Ada", "me@example.org")
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val own = OwnAddresses(mapOf("ada" to listOf(me.address)))

    private fun message(sender: MailSender = grace) =
        MailMessage(
            id = "ada/e1",
            sender = sender,
            subject = "Q4 launch plan",
            body = "",
            received = ReceivedAt("2026-10-12", "09:14"),
            location = MailLocation("Inbox", "Ada", "ada"),
            to = listOf(me),
        )

    private fun ReadMenu.parts() = sections.map { it.part }

    private fun ReadMenu.first() = sections.first().entries

    @Test
    fun onlyAnOlderThreadPageOffersDeletingThisMessage() {
        assertEquals(
            ReadMenuEntry.DeleteThisMessage,
            readMenu(ReadPage(message(), own, index = 4, count = 6)).first().last(),
        )
        assertFalse(ReadMenuEntry.DeleteThisMessage in readMenu(ReadPage(message(), own, index = 0, count = 6)).first())
    }

    @Test
    fun aThreadMovesAndMutesFromItsConversationSection() {
        val menu = readMenu(ReadPage(message(), own, count = 6, muted = true))
        assertEquals(
            listOf(ReadMenuPart.ThisMessage, ReadMenuPart.Sender("Grace Hopper"), ReadMenuPart.Conversation),
            menu.parts(),
        )
        assertTrue(ReadMenuEntry.Mute(on = true) in menu.sections.last().entries)
        assertTrue(menu.first().none { it is ReadMenuEntry.Act && it.action == MailAction.Move })
    }

    @Test
    fun aSingleMessageMovesFromItsOwnSection() {
        val menu = readMenu(ReadPage(message(), own))
        assertEquals(listOf(ReadMenuPart.ThisMessage, ReadMenuPart.Sender("Grace Hopper")), menu.parts())
        assertTrue(menu.first().any { it is ReadMenuEntry.Act && it.action == MailAction.Move })
    }

    @Test
    fun theUsersOwnMessageHasNoReplySenderSectionOrSpam() {
        val menu = readMenu(ReadPage(message(sender = me), own, count = 2))
        assertEquals(listOf(ReadMenuPart.ThisMessage, ReadMenuPart.Conversation), menu.parts())
        assertEquals(setOf(MailAction.Reply, MailAction.ReplyAll, MailAction.Spam), menu.withheld)
    }

    @Test
    fun aNewsletterWithholdsRepliesAndOffersImagesWhileBlocked() {
        val menu = readMenu(ReadPage(message(), own, newsletter = true, imagesBlocked = true))
        assertEquals(setOf(MailAction.Reply, MailAction.ReplyAll), menu.withheld)
        assertEquals(ReadMenuEntry.AlwaysShowImages("Grace Hopper"), menu.first().first())
        assertFalse(
            readMenu(ReadPage(message(), own, newsletter = true)).first().any { it is ReadMenuEntry.AlwaysShowImages },
        )
    }
}
