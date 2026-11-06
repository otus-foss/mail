package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveSearchTest {
    private val targets =
        listOf(
            MoveTarget("t", listOf("Deleted Items"), MailboxRole.Trash),
            MoveTarget("c", listOf("Clients"), name = "Clients", own = true),
            MoveTarget("j", listOf("Junk Mail"), MailboxRole.Junk),
            MoveTarget("ca", listOf("Clients", "Acme"), name = "Acme", depth = 1, own = true),
            MoveTarget("s", listOf("Sent")),
            MoveTarget("x", listOf("Old mail"), MailboxRole.Archive),
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
