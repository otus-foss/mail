package com.otusfoss.mail.feature.inbox.presentation.actions

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.ActionSwitch
import com.otusfoss.mail.core.messages.presentation.actions.DraftSubject
import com.otusfoss.mail.core.messages.presentation.actions.PageExtras
import com.otusfoss.mail.core.messages.presentation.actions.PageSubject
import com.otusfoss.mail.core.messages.presentation.actions.offer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** What only the open page or the draft can do: mute, images, block, unsubscribe, and Schedule send. */
class PageOffersTest {
    private val flips = mutableListOf<Boolean>()

    private fun switch(on: Boolean) = ActionSwitch(on) { flips += it }

    private fun page(extras: PageExtras = PageExtras()): ActionSubject {
        val message = offerMessage("1")
        return ActionSubject(
            offerActions(),
            listOf(message),
            page = PageSubject(message, MailboxRole.Inbox, onCompose = {}, extras = extras),
        )
    }

    @Test
    fun aSwitchSaysWhatPressingItDoesNext() {
        val off = assertNotNull(page(PageExtras(mute = switch(on = false))).offer(MailAction.Mute))
        assertEquals("Mute conversation", off.label)
        off.run()
        val on = assertNotNull(page(PageExtras(mute = switch(on = true))).offer(MailAction.Mute))
        assertEquals("Unmute conversation", on.label)
        on.run()
        assertEquals(listOf(true, false), flips)
    }

    @Test
    fun shownImagesOfferToHideThemAgain() {
        val label = { shown: Boolean -> page(PageExtras(images = switch(shown))).offer(MailAction.ShowImages)?.label }
        assertEquals("Hide images", label(true))
        assertEquals("Show images", label(false))
    }

    @Test
    fun aPageThatCannotTakeAnExtraOffersNone() {
        val actions = listOf(MailAction.Mute, MailAction.ShowImages, MailAction.BlockSender, MailAction.Unsubscribe)
        actions.forEach { assertNull(page().offer(it), "Offering $it") }
        val able = page(PageExtras(onBlock = {}, onUnsubscribe = {}))
        assertNotNull(able.offer(MailAction.BlockSender))
        assertNotNull(able.offer(MailAction.Unsubscribe))
    }

    @Test
    fun scheduleSendOpensItsChoiceWhereTheAccountCanDelayASend() {
        var opened = 0
        val draft = { scheduled: Boolean, onSchedule: (() -> Unit)? ->
            val subject = DraftSubject(onSend = {}, onAttach = null, onDiscard = {}, scheduled, onSchedule)
            ActionSubject(actions = null, draft = subject)
        }
        assertNull(draft(false, null).offer(MailAction.ScheduleSend))
        val offered = assertNotNull(draft(false) { opened++ }.offer(MailAction.ScheduleSend))
        assertEquals("Schedule send" to true, offered.label to offered.asks)
        offered.run()
        assertEquals(1, opened)
        assertEquals("Change send time", draft(true) {}.offer(MailAction.ScheduleSend)?.label)
    }
}
