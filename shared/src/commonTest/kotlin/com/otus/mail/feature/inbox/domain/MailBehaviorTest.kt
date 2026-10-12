package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailBehaviorTest {
    @Test
    fun readLaterIsASwipeChoiceOnlyWhileItIsOn() {
        assertFalse(MailAction.ReadLater in MailBehavior().swipeChoices)
        assertTrue(MailAction.ReadLater in MailBehavior(readLater = true).swipeChoices)
    }

    @Test
    fun turningReadLaterOffReturnsItsSwipesToTheirDefaults() {
        val on = MailBehavior(readLater = true, startToEnd = MailAction.ReadLater, endToStart = MailAction.Trash)
        assertEquals(on, on.withReadLater(true))
        assertEquals(MailBehavior(endToStart = MailAction.Trash), on.withReadLater(false))
    }

    @Test
    fun readLaterAddsItsPillToTheEndOfTheRowWhileItIsOn() {
        val flagged = MailBehavior(pills = listOf(InboxPill.Filter.Flagged))
        val on = flagged.withReadLater(true)
        assertEquals(listOf(InboxPill.Filter.Flagged, InboxPill.ReadLater), on.pills)
        val reordered = on.copy(pills = on.pills.reversed())
        assertEquals(reordered, reordered.withReadLater(true), "Turning it on again keeps the row as it is")
        assertEquals(listOf(InboxPill.Filter.Flagged), on.withReadLater(false).pills)
    }

    @Test
    fun turningReadLaterOffTakesItOutOfEveryBar() {
        val on =
            MailBehavior(readLater = true).copy(
                actionBar =
                    mapOf(
                        ActionBarPlace.Message to
                            ActionBarLayout(listOf(MailAction.ReadLater, MailAction.Trash), MailAction.Move),
                        ActionBarPlace.Selection to
                            ActionBarLayout(
                                listOf(MailAction.Read),
                                MailAction.ReadLater,
                            ),
                    ),
            )
        val off = on.withReadLater(false)
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
    fun readLaterIsABarChoiceOnlyWhileItIsOn() {
        assertFalse(MailAction.ReadLater in MailBehavior().barChoices(ActionBarPlace.Message))
        assertTrue(MailAction.ReadLater in MailBehavior(readLater = true).barChoices(ActionBarPlace.Message))
    }

    @Test
    fun selectedMessagesNeverOfferARuleForTheSender() {
        val behavior = MailBehavior(readLater = true)
        assertTrue(MailAction.RuleForSender in behavior.barChoices(ActionBarPlace.Message))
        assertFalse(MailAction.RuleForSender in behavior.barChoices(ActionBarPlace.Selection))
    }

    @Test
    fun eachPlaceShowsItsOwnBarUntilOneBarIsFixedForAll() {
        val message = ActionBarLayout(listOf(MailAction.Flag), MailAction.Reply)
        val everywhere = ActionBarLayout(listOf(MailAction.Search), MailAction.Archive)
        val adaptive =
            MailBehavior().copy(
                actionBar =
                    MailBehavior().actionBar +
                        mapOf(ActionBarPlace.Message to message, ActionBarPlace.Everywhere to everywhere),
            )
        assertEquals(ActionBarMode.Adaptive, MailBehavior().actionBarMode)
        assertEquals(message, adaptive.barLayout(ActionBarPlace.Message))
        assertEquals(ActionBarPlace.Inbox.defaultLayout, adaptive.barLayout(ActionBarPlace.Inbox))
        val fixed = adaptive.copy(actionBarMode = ActionBarMode.Fixed)
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
        val sparse = MailBehavior(actionBar = emptyMap())
        ActionBarPlace.entries.forEach { assertEquals(it.defaultLayout, sparse.barLayout(it)) }
    }

    @Test
    fun aCopiedBarKeepsWhatTheTargetOffersAndItsOwnMainButtonOtherwise() {
        val behavior = MailBehavior()
        val selection =
            ActionBarLayout(listOf(MailAction.Archive, MailAction.Trash), MailAction.Move)
        assertEquals(selection, behavior.copiedBar(ActionBarPlace.Message, ActionBarPlace.Selection))
        assertEquals(
            ActionBarPlace.Message.defaultLayout,
            behavior.copiedBar(ActionBarPlace.Message, ActionBarPlace.Everywhere),
        )
        // Writing offers none of the buttons, so it keeps only its own main button.
        assertEquals(
            ActionBarLayout(emptyList(), MailAction.Send),
            behavior.copiedBar(ActionBarPlace.Message, ActionBarPlace.Compose),
        )
    }
}
