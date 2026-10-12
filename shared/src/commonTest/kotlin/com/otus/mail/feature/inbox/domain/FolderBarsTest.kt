package com.otus.mail.feature.inbox.domain

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
        assertEquals(ActionBarLayout(listOf(DeleteForever), NotSpam), behavior.barLayout(Message, FolderRole.Junk))
        assertEquals(ActionBarLayout(listOf(DeleteForever), NotSpam), behavior.barLayout(Selection, FolderRole.Junk))
        assertEquals(list, behavior.barLayout(Inbox, FolderRole.Junk))
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), behavior.barLayout(Message, FolderRole.Trash))
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), behavior.barLayout(Selection, FolderRole.Trash))
        assertEquals(list, behavior.barLayout(Inbox, FolderRole.Trash))
    }

    @Test
    fun sentFollowsUpOnAnOpenMessageAndKeepsTheOtherBars() {
        assertEquals(ActionBarLayout(listOf(Forward), FollowUp), behavior.barLayout(Message, FolderRole.Sent))
        listOf(Inbox, Selection, Compose).forEach {
            assertEquals(it.defaultLayout, behavior.barLayout(it, FolderRole.Sent))
        }
    }

    @Test
    fun otherFoldersAndTheComposerKeepTheirBars() {
        ActionBarPlace.entries.forEach { assertEquals(behavior.barLayout(it), behavior.barLayout(it, null)) }
        FolderRole.entries.forEach { assertEquals(Compose.defaultLayout, behavior.barLayout(Compose, it)) }
    }

    @Test
    fun aBarTheUserChangedLooksTheSameInEveryFolder() {
        val custom = ActionBarLayout(listOf(MailAction.Archive), MailAction.Move)
        val changed = behavior.copy(actionBar = behavior.actionBar + (Message to custom))
        assertEquals(custom, changed.barLayout(Message, FolderRole.Trash))
        // Other places the user left alone still change with the folder.
        assertEquals(ActionBarLayout(listOf(DeleteForever), Restore), changed.barLayout(Selection, FolderRole.Trash))
    }

    @Test
    fun theSameEverywhereBarIgnoresTheFolder() {
        val fixed = behavior.copy(actionBarMode = ActionBarMode.Fixed)
        FolderRole.entries.forEach { role ->
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

    @Test
    fun rolesAreFoundByTheirServerName() {
        assertEquals(FolderRole.Junk, FolderRole.of("junk"))
        assertEquals(null, FolderRole.of("inbox"))
        assertEquals(null, FolderRole.of(null))
    }
}
