package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ActionBarPlace.Compose
import com.otusfoss.mail.core.messages.domain.ActionBarPlace.Inbox
import com.otusfoss.mail.core.messages.domain.ActionBarPlace.Message
import com.otusfoss.mail.core.messages.domain.ActionBarPlace.Selection
import com.otusfoss.mail.core.messages.domain.MailAction.DeleteForever
import com.otusfoss.mail.core.messages.domain.MailAction.EditScheduled
import com.otusfoss.mail.core.messages.domain.MailAction.EmptyFolder
import com.otusfoss.mail.core.messages.domain.MailAction.Filters
import com.otusfoss.mail.core.messages.domain.MailAction.FollowUp
import com.otusfoss.mail.core.messages.domain.MailAction.Forward
import com.otusfoss.mail.core.messages.domain.MailAction.NewMail
import com.otusfoss.mail.core.messages.domain.MailAction.NotSpam
import com.otusfoss.mail.core.messages.domain.MailAction.Restore
import com.otusfoss.mail.core.messages.domain.MailAction.Search
import com.otusfoss.mail.core.messages.domain.MailAction.SendNow
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderBarsTest {
    private val behavior = ActionBehavior()

    @Test
    fun spamAndTrashOfferTheirWayOutAndDeletingForGood() {
        val list = ActionBarLayout(listOf(Search, Filters, EmptyFolder), NewMail)
        assertEquals(ActionBarLayout(listOf(DeleteForever), NotSpam), behavior.barLayout(Message, MailboxRole.Junk))
        assertEquals(ActionBarLayout(listOf(DeleteForever), NotSpam), behavior.barLayout(Selection, MailboxRole.Junk))
        assertEquals(list, behavior.barLayout(Inbox, MailboxRole.Junk))
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), behavior.barLayout(Message, MailboxRole.Trash))
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), behavior.barLayout(Selection, MailboxRole.Trash))
        assertEquals(list, behavior.barLayout(Inbox, MailboxRole.Trash))
    }

    @Test
    fun sentFollowsUpOnAnOpenMessageAndKeepsTheOtherBars() {
        assertEquals(ActionBarLayout(listOf(Forward), FollowUp), behavior.barLayout(Message, MailboxRole.Sent))
        listOf(Inbox, Selection, Compose).forEach {
            assertEquals(it.defaultLayout, behavior.barLayout(it, MailboxRole.Sent))
        }
    }

    @Test
    fun otherFoldersAndTheComposerKeepTheirBars() {
        ActionBarPlace.entries.forEach { assertEquals(behavior.barLayout(it), behavior.barLayout(it, null)) }
        MailboxRole.entries.forEach { assertEquals(Compose.defaultLayout, behavior.barLayout(Compose, it)) }
    }

    @Test
    fun aBarTheUserChangedLooksTheSameInEveryFolder() {
        val custom = ActionBarLayout(listOf(MailAction.Archive), MailAction.Move)
        val changed = behavior.copy(bars = behavior.bars + (Message to custom))
        assertEquals(custom, changed.barLayout(Message, MailboxRole.Trash))
        // Other places the user left alone still change with the folder.
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), changed.barLayout(Selection, MailboxRole.Trash))
    }

    @Test
    fun theSameEverywhereBarIgnoresTheFolder() {
        val fixed = behavior.copy(barMode = ActionBarMode.Fixed)
        (MailboxRole.entries - MailboxRole.Scheduled).forEach { role ->
            assertEquals(ActionBarPlace.Everywhere.defaultLayout, fixed.barLayout(Message, role))
        }
    }

    @Test
    fun scheduledMailKeepsEditAndSendNowInEveryMode() {
        val own = ActionBarLayout(listOf(SendNow), EditScheduled)
        assertEquals(own, behavior.copy(barMode = ActionBarMode.Fixed).barLayout(Message, MailboxRole.Scheduled))
        assertEquals(own, behavior.barLayout(Message, MailboxRole.Scheduled))
        assertTrue(EditScheduled !in ActionBarPlace.Everywhere.actions && SendNow !in Message.actions)
    }

    @Test
    fun theEditorOffersTheNewActionsWhereTheyApplyAndSavesThem() {
        assertTrue(listOf(NotSpam, Restore, DeleteForever).all { it in Selection.actions && it in Message.actions })
        assertTrue(FollowUp in Message.actions && FollowUp !in Selection.actions)
        assertTrue(EmptyFolder in Inbox.actions && EmptyFolder !in Message.actions)
        val saved = ActionBarLayout(listOf(DeleteForever, Restore), NotSpam)
        assertEquals(saved, ActionBarLayout.decode(saved.encode(), Selection))
        assertEquals("delete_forever,restore|not_spam", saved.encode())
    }
}
