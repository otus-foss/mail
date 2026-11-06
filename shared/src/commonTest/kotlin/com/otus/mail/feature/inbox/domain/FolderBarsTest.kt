package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Compose
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Inbox
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Message
import com.otus.mail.feature.inbox.domain.ActionBarPlace.Selection
import com.otus.mail.feature.inbox.domain.MailAction.DeleteForever
import com.otus.mail.feature.inbox.domain.MailAction.EmptyFolder
import com.otus.mail.feature.inbox.domain.MailAction.Filters
import com.otus.mail.feature.inbox.domain.MailAction.FollowUp
import com.otus.mail.feature.inbox.domain.MailAction.Forward
import com.otus.mail.feature.inbox.domain.MailAction.NewMail
import com.otus.mail.feature.inbox.domain.MailAction.NotSpam
import com.otus.mail.feature.inbox.domain.MailAction.Restore
import com.otus.mail.feature.inbox.domain.MailAction.Search
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderBarsTest {
    private val behavior = MailBehavior()

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
        val changed = behavior.copy(actionBar = behavior.actionBar + (Message to custom))
        assertEquals(custom, changed.barLayout(Message, MailboxRole.Trash))
        // Other places the user left alone still change with the folder.
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), changed.barLayout(Selection, MailboxRole.Trash))
    }

    @Test
    fun theSameEverywhereBarIgnoresTheFolder() {
        val fixed = behavior.copy(actionBarMode = ActionBarMode.Fixed)
        MailboxRole.entries.forEach { role ->
            assertEquals(ActionBarPlace.Everywhere.defaultLayout, fixed.barLayout(Message, role))
        }
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
