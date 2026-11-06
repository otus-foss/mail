package com.otus.mail.app

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Values builds before the shared mailbox model saved still read as the same pages, pins, and pills. */
class OldSavedFormatsTest {
    @Test
    fun combinedPagesReadTheOldFolderNamesAndTheRoles() {
        assertEquals(MailboxRole.Junk, MailPage.combinedRole("Spam"))
        listOf("Drafts", "Sent", "Archive", "Trash").forEach { old ->
            assertEquals(old, MailPage.combinedRole(old)?.name)
        }
        assertEquals(MailboxRole.Junk, MailPage.combinedRole("junk"))
        assertNull(MailPage.combinedRole("inbox"), "The inbox has a page of its own")
    }

    @Test
    fun rolePinsAndPillsKeepTheirStoredValues() {
        assertEquals(SidebarPin.Role(MailboxRole.Junk), SidebarPin.fromStorageValue("role:junk"))
        assertEquals("role:junk", SidebarPin.Role(MailboxRole.Junk).storageValue)
        assertEquals(InboxPill.Role(MailboxRole.Archive), InboxPill.fromStorageValue("role:archive"))
        val important = InboxPill.fromStorageValue("role:important")
        assertEquals(InboxPill.OtherRole("important"), important)
        assertEquals("role:important", important?.storageValue)
    }
}
