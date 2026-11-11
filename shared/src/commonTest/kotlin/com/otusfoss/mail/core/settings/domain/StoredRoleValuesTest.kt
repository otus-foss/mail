package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

/** Values builds before the shared mailbox model saved still read as the same pins and pills. */
class StoredRoleValuesTest {
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
