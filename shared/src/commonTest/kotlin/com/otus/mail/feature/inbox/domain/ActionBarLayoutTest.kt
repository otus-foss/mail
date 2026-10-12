package com.otus.mail.feature.inbox.domain

import com.otus.mail.feature.inbox.domain.ActionBarPlace.Compose
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Everywhere
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Inbox
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Message
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Selection
import com.otus.mail.feature.inbox.domain.MailAction.Archive
import com.otus.mail.feature.inbox.domain.MailAction.Attach
import com.otus.mail.feature.inbox.domain.MailAction.Discard
import com.otus.mail.feature.inbox.domain.MailAction.Filters
import com.otus.mail.feature.inbox.domain.MailAction.Flag
import com.otus.mail.feature.inbox.domain.MailAction.MarkAllRead
import com.otus.mail.feature.inbox.domain.MailAction.Move
import com.otus.mail.feature.inbox.domain.MailAction.NewMail
import com.otus.mail.feature.inbox.domain.MailAction.Read
import com.otus.mail.feature.inbox.domain.MailAction.ReadLater
import com.otus.mail.feature.inbox.domain.MailAction.Reply
import com.otus.mail.feature.inbox.domain.MailAction.RuleForSender
import com.otus.mail.feature.inbox.domain.MailAction.Search
import com.otus.mail.feature.inbox.domain.MailAction.Send
import com.otus.mail.feature.inbox.domain.MailAction.Spam
import com.otus.mail.feature.inbox.domain.MailAction.Trash
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionBarLayoutTest {
    private fun layout(
        vararg secondary: MailAction,
        primary: MailAction = Move,
    ) = ActionBarLayout(secondary.toList(), primary)

    private val message = layout(Archive, Trash, Flag)

    @Test
    fun aSlotTakesANewActionOrEmptiesAndLaterSlotsMoveUp() {
        assertEquals(layout(Spam, Trash, Flag), message.withSlot(0, Spam))
        assertEquals(layout(Archive, Flag), message.withSlot(1, null))
        assertEquals(layout(Archive, Trash), layout(Archive).withSlot(2, Trash))
    }

    @Test
    fun anActionAlreadyInTheBarTradesPlacesWithTheSlotsOldAction() {
        assertEquals(layout(Flag, Trash, Archive), message.withSlot(0, Flag))
        assertEquals(layout(Move, Trash, Flag, primary = Archive), message.withSlot(0, Move))
    }

    @Test
    fun aPrimaryChosenFromTheSecondaryTradesPlacesWithTheOldPrimary() {
        assertEquals(layout(Archive, Trash, Move, primary = Flag), message.with(BarSlot.Primary, Flag))
        assertEquals(layout(Archive, Trash, Flag, primary = Spam), message.with(BarSlot.Primary, Spam))
    }

    @Test
    fun aSecondarySlotTakesANewActionOrTradesWithAnotherSlot() {
        assertEquals(layout(Spam, Trash, Flag), message.with(BarSlot.Secondary(0), Spam))
        assertEquals(layout(Flag, Trash, Archive), message.with(BarSlot.Secondary(0), Flag))
        assertEquals(layout(Archive, Trash), layout(Archive).with(BarSlot.Secondary(1), Trash))
    }

    @Test
    fun slotsNameWhereEachActionSits() {
        assertEquals(BarSlot.Primary, message.slotOf(Move))
        assertEquals(BarSlot.Secondary(2), message.slotOf(Flag))
        assertEquals(null, message.slotOf(Spam))
        assertEquals(Trash, message.actionAt(BarSlot.Secondary(1)))
        assertEquals(null, layout(Archive).actionAt(BarSlot.Secondary(2)))
    }

    @Test
    fun theButtonsListThePrimaryFirst() {
        assertEquals(listOf(Move, Archive, Trash, Flag), message.buttons)
    }

    @Test
    fun anEmptySlotCannotTakeThePrimary() {
        val short = layout(Archive)
        assertEquals(short, short.withSlot(1, Move))
    }

    @Test
    fun aDroppedPrimaryReturnsToThePlacesDefault() {
        assertEquals(layout(Archive, Flag), message.without(Trash, Message))
        assertEquals(layout(Archive, primary = Reply), layout(Archive, primary = ReadLater).without(ReadLater, Message))
        // The default primary itself falls back to the first remaining slot.
        assertEquals(
            layout(Trash, Flag, primary = Archive),
            layout(Archive, Trash, Flag, primary = Reply).without(Reply, Message),
        )
    }

    @Test
    fun aLayoutReadsBackAsSaved() {
        assertEquals("archive,trash,flag|move", message.encode())
        listOf(message, layout(primary = Spam), layout(Read, ReadLater, Trash, primary = Flag)).forEach {
            assertEquals(it, ActionBarLayout.decode(it.encode(), Message))
        }
    }

    @Test
    fun aValueTheBarCannotHoldReadsAsTheDefault() {
        listOf(
            null,
            "",
            "archive,trash",
            "archive|trash|move",
            "archive,bogus|move",
            "archive,archive|move",
            "archive|archive",
            "archive,trash,flag,spam|move",
        ).forEach { assertEquals(Message.defaultLayout, ActionBarLayout.decode(it, Message), "Decoding $it") }
    }

    @Test
    fun onlyAnOpenMessageCanHoldARuleForTheSender() {
        val saved = layout(RuleForSender).encode()
        assertEquals(layout(RuleForSender), ActionBarLayout.decode(saved, Message))
        assertEquals(Selection.defaultLayout, ActionBarLayout.decode(saved, Selection))
    }

    @Test
    fun eachPlaceStartsWithTheSpecsLayout() {
        assertEquals(layout(Search, Filters, MarkAllRead, primary = NewMail), Inbox.defaultLayout)
        assertEquals(layout(Read, Flag, Trash), Selection.defaultLayout)
        assertEquals(layout(Archive, Trash, Move, primary = Reply), Message.defaultLayout)
        assertEquals(layout(Attach, Discard, primary = Send), Compose.defaultLayout)
        assertEquals(layout(Search, Archive, Trash, primary = NewMail), Everywhere.defaultLayout)
        ActionBarPlace.entries.forEach { place ->
            assertEquals(place.defaultLayout, ActionBarLayout.decode(place.defaultLayout.encode(), place))
        }
    }

    @Test
    fun aPlaceHoldsOnlyActionsForWhatItShows() {
        val reply = layout(Archive, primary = Reply).encode()
        assertEquals(layout(Archive, primary = Reply), ActionBarLayout.decode(reply, Message))
        assertEquals(Selection.defaultLayout, ActionBarLayout.decode(reply, Selection))
        assertEquals(Inbox.defaultLayout, ActionBarLayout.decode(reply, Inbox))
        assertEquals(Compose.defaultLayout, ActionBarLayout.decode(reply, Compose))
        val mixed = layout(Search, Reply, Send, primary = MarkAllRead)
        assertEquals(mixed, ActionBarLayout.decode(mixed.encode(), Everywhere))
        assertEquals(listOf(Inbox, Selection, Message, Compose), ActionBarPlace.entries.filter { it.adaptive })
    }
}
