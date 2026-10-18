package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class InboxContentsTest {
    @Test
    fun theDefaultListsTheInboxAlone() {
        assertEquals(listOf("inbox"), InboxContents().roles)
    }

    @Test
    fun spamAndTrashJoinTheInboxRoles() {
        assertEquals(listOf("inbox", "junk", "trash"), InboxContents(spam = true, trash = true).roles)
        assertEquals(listOf("inbox", "trash"), InboxContents(trash = true).roles)
    }

    @Test
    fun archiveJoinsTheRolesAndOwnFoldersAreSavedApart() {
        val contents = InboxContents(archive = true, ownFolders = true)
        assertEquals(listOf("inbox", "archive"), contents.roles)
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
        val saved = "spam=false\ntrash=true\naddress=ada\thi@design.ch"
        assertEquals(InboxContents(trash = true), InboxContents.decode(saved))
    }
}
