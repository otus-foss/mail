package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveSearchTest {
    private val targets =
        listOf(
            MoveTarget("t", "Deleted Items", MailboxRole.Trash),
            MoveTarget("c", "Clients", name = "Clients", own = true),
            MoveTarget("j", "Junk Mail", MailboxRole.Junk),
            MoveTarget("ca", "Clients / Acme", name = "Acme", depth = 1, own = true),
            MoveTarget("s", "Sent"),
            MoveTarget("x", "Old mail", MailboxRole.Archive),
        )

    @Test
    fun systemFoldersFollowThePillOrderAndOwnFoldersKeepTreeOrder() {
        assertEquals(listOf("x", "j", "t"), targets.shortcuts().map { it.folderId })
        assertEquals(listOf("c", "ca"), targets.ownFolders().map { it.folderId })
    }

    @Test
    fun searchMatchesNamePathOrPillLabelIgnoringCase() {
        val acme = targets.first { it.folderId == "ca" }
        assertTrue(acme.matchesSearch("acme"))
        assertTrue(acme.matchesSearch("CLIENTS / a"))
        assertTrue(acme.matchesSearch("  "))
        assertFalse(acme.matchesSearch("receipts"))
        // Servers name the junk folder differently; the pill's own label still finds it.
        assertTrue(targets.first { it.folderId == "j" }.matchesSearch("spam"))
        assertEquals(listOf("c", "ca"), targets.filter { it.matchesSearch("client") }.map { it.folderId })
    }
}
