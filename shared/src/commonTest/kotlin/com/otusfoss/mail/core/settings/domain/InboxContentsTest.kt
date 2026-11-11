package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

class InboxContentsTest {
    @Test
    fun theDefaultListsTheInboxAlone() {
        assertEquals(listOf(MailboxRole.Inbox), InboxContents().roles)
    }

    @Test
    fun spamAndTrashJoinTheInboxRoles() {
        assertEquals(
            listOf(MailboxRole.Inbox, MailboxRole.Junk, MailboxRole.Trash),
            InboxContents(spam = true, trash = true).roles,
        )
        assertEquals(listOf(MailboxRole.Inbox, MailboxRole.Trash), InboxContents(trash = true).roles)
    }

    @Test
    fun archiveJoinsTheRolesAndOwnFoldersAreSavedApart() {
        val contents = InboxContents(archive = true, ownFolders = true)
        assertEquals(listOf(MailboxRole.Inbox, MailboxRole.Archive), contents.roles)
        assertEquals(contents, InboxContents.decode(InboxContents.encode(contents)))
    }

    @Test
    fun savedContentsReadBackAndUnknownValuesReadAsDefaults() {
        val contents = InboxContents(spam = true)
        assertEquals(contents, InboxContents.decode(InboxContents.encode(contents)))
        assertEquals(InboxContents(), InboxContents.decode("spam=maybe\nnewer=1"))
        assertEquals(InboxContents(), InboxContents.decode(""))
    }

    @Test
    fun addressesSavedByOlderVersionsAreIgnored() {
        val saved = "spam=false\ntrash=true\naddress=ada\thi@ruu.by"
        assertEquals(InboxContents(trash = true), InboxContents.decode(saved))
    }
}
