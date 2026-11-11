package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.RemoteImages
import kotlin.test.Test
import kotlin.test.assertEquals

class MailBehaviorTest {
    @Test
    fun turningReadLaterOffReturnsItsSwipesToTheirDefaults() {
        val on =
            MailBehavior(
                readLater = true,
                actions = ActionBehavior(startToEnd = MailAction.ReadLater, endToStart = MailAction.Trash),
            )
        assertEquals(on, on.withReadLater(true))
        assertEquals(MailBehavior(actions = ActionBehavior(endToStart = MailAction.Trash)), on.withReadLater(false))
    }

    @Test
    fun readLaterAddsItsPillToTheEndOfTheRowWhileItIsOn() {
        val flagged = MailBehavior(list = ListBehavior(pills = listOf(InboxPill.Filter.Flagged)))
        val on = flagged.withReadLater(true)
        assertEquals(listOf(InboxPill.Filter.Flagged, InboxPill.ReadLater), on.list.pills)
        val reordered = on.copy(list = on.list.copy(pills = on.list.pills.reversed()))
        assertEquals(reordered, reordered.withReadLater(true), "Turning it on again keeps the row as it is")
        assertEquals(listOf(InboxPill.Filter.Flagged), on.withReadLater(false).list.pills)
    }

    @Test
    fun turningReadLaterOffTakesItOutOfEveryBar() {
        val on =
            MailBehavior(
                readLater = true,
                actions =
                    ActionBehavior(
                        bars =
                            mapOf(
                                ActionBarPlace.Message to
                                    ActionBarLayout(listOf(MailAction.ReadLater, MailAction.Trash), MailAction.Move),
                                ActionBarPlace.Selection to
                                    ActionBarLayout(
                                        listOf(MailAction.Read),
                                        MailAction.ReadLater,
                                    ),
                            ),
                    ),
            )
        val off = on.withReadLater(false).actions
        assertEquals(
            ActionBarLayout(listOf(MailAction.Trash), MailAction.Move),
            off.barLayout(ActionBarPlace.Message),
        )
        assertEquals(
            ActionBarLayout(listOf(MailAction.Read), MailAction.Move),
            off.barLayout(ActionBarPlace.Selection),
        )
    }

    @Test
    fun imagesLoadFromPeopleYouKnowByDefault() = assertEquals(RemoteImages.Known, MailBehavior().read.remoteImages)
}
