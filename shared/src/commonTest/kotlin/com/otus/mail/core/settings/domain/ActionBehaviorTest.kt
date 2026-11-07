package com.otus.mail.core.settings.domain

import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.BarButton
import com.otus.mail.core.messages.domain.MailAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionBehaviorTest {
    @Test
    fun readLaterIsASwipeChoiceOnlyWhileItIsOn() {
        assertFalse(MailAction.ReadLater in ActionBehavior().swipeChoices(readLater = false))
        assertTrue(MailAction.ReadLater in ActionBehavior().swipeChoices(readLater = true))
    }

    @Test
    fun readLaterIsABarChoiceOnlyWhileItIsOn() {
        assertFalse(MailAction.ReadLater in ActionBehavior().barChoices(ActionBarPlace.Message, readLater = false))
        assertTrue(MailAction.ReadLater in ActionBehavior().barChoices(ActionBarPlace.Message, readLater = true))
    }

    @Test
    fun everyBarCanHoldAllActions() {
        ActionBarPlace.entries.forEach {
            assertTrue(MailAction.AllActions in ActionBehavior().barChoices(it, readLater = false))
        }
    }

    @Test
    fun selectedMessagesNeverOfferARuleForTheSender() {
        val actions = ActionBehavior()
        assertTrue(MailAction.RuleForSender in actions.barChoices(ActionBarPlace.Message, readLater = true))
        assertFalse(MailAction.RuleForSender in actions.barChoices(ActionBarPlace.Selection, readLater = true))
    }

    @Test
    fun eachPlaceShowsItsOwnBarUntilOneBarIsFixedForAll() {
        val message = ActionBarLayout(listOf(MailAction.Flag), MailAction.Reply)
        val everywhere = ActionBarLayout(listOf(MailAction.Search), MailAction.Archive)
        val adaptive =
            ActionBehavior().copy(
                bars =
                    ActionBehavior().bars +
                        mapOf(ActionBarPlace.Message to message, ActionBarPlace.Everywhere to everywhere),
            )
        assertEquals(ActionBarMode.Adaptive, ActionBehavior().barMode)
        assertEquals(message, adaptive.barLayout(ActionBarPlace.Message))
        assertEquals(ActionBarPlace.Inbox.defaultLayout, adaptive.barLayout(ActionBarPlace.Inbox))
        val fixed = adaptive.copy(barMode = ActionBarMode.Fixed)
        ActionBarPlace.entries.forEach { assertEquals(everywhere, fixed.barLayout(it), "Fixed bar at $it") }
        assertEquals(message, fixed.savedLayout(ActionBarPlace.Message), "Fixed mode keeps each place's own bar")
    }

    @Test
    fun aModeReadsBackAsSavedAndAnUnknownOneReadsAsNothing() {
        ActionBarMode.entries.forEach { assertEquals(it, ActionBarMode.fromStorageValue(it.storageValue)) }
        assertEquals(null, ActionBarMode.fromStorageValue("bogus"))
        assertEquals(null, ActionBarMode.fromStorageValue(null))
    }

    @Test
    fun aMissingPlaceShowsItsDefault() {
        val sparse = ActionBehavior(bars = emptyMap())
        ActionBarPlace.entries.forEach { assertEquals(it.defaultLayout, sparse.barLayout(it)) }
    }

    @Test
    fun aCopiedBarKeepsWhatTheTargetOffersAndItsOwnMainButtonOtherwise() {
        val actions = ActionBehavior()
        val selection =
            ActionBarLayout(listOf(MailAction.Archive, MailAction.Trash), MailAction.Move)
        assertEquals(selection, actions.copiedBar(ActionBarPlace.Message, ActionBarPlace.Selection, readLater = false))
        assertEquals(
            ActionBarPlace.Message.defaultLayout,
            actions.copiedBar(ActionBarPlace.Message, ActionBarPlace.Everywhere, readLater = false),
        )
        // Writing offers none of the buttons, so it keeps only its own main button.
        assertEquals(
            ActionBarLayout(emptyList(), MailAction.Send),
            actions.copiedBar(ActionBarPlace.Message, ActionBarPlace.Compose, readLater = false),
        )
    }

    @Test
    fun aCopiedMoveToButtonGoesOnlyWhereMessagesCanMove() {
        val receipts = BarButton.MoveTo(listOf("Receipts"))
        val message = ActionBarLayout(listOf(MailAction.Archive, receipts), MailAction.Reply)
        val actions = ActionBehavior(bars = mapOf(ActionBarPlace.Message to message))
        assertEquals(
            ActionBarLayout(listOf(MailAction.Archive, receipts), MailAction.Move),
            actions.copiedBar(ActionBarPlace.Message, ActionBarPlace.Selection, readLater = false),
        )
        assertEquals(
            ActionBarLayout(emptyList(), MailAction.NewMail),
            actions.copiedBar(ActionBarPlace.Message, ActionBarPlace.Inbox, readLater = false),
        )
    }
}
