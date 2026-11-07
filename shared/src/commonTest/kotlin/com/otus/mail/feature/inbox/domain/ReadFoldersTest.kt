package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.core.messages.domain.displayPaths
import com.otus.mail.core.messages.domain.folderHint
import com.otus.mail.core.messages.domain.inJunk
import com.otus.mail.core.messages.domain.orderedFolders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadFoldersTest {
    private val inbox = MessageFolder("in", listOf("Inbox"), role = MailboxRole.Inbox)
    private val drafts = MessageFolder("dr", listOf("Drafts"), role = MailboxRole.Drafts)
    private val readLater = MessageFolder("rl", listOf("Read later"), readLater = true)
    private val clients = MessageFolder("cl", listOf("Clients"), color = "blue")
    private val financeInvoices = MessageFolder("fi", listOf("Finance", "Invoices"), color = "yellow")
    private val freelancing = MessageFolder("fr", listOf("Freelancing"), color = "pink")
    private val freelancingInvoices = MessageFolder("fri", listOf("Freelancing", "Invoices"), color = "pink")
    private val archive = MessageFolder("ar", listOf("Archive 2025"), color = "green")
    private val all =
        listOf(inbox, drafts, readLater, clients, financeInvoices, freelancing, freelancingInvoices, archive)
            .associateBy { it.id }

    @Test
    fun leavesOutTheFolderTheUserCameFrom() {
        val hint = folderHint(setOf("cl", "fi"), all, cameFrom = setOf("cl"))
        assertTrue(hint.also)
        assertEquals(listOf(financeInvoices), hint.folders)
    }

    @Test
    fun onlyTheFolderTheUserCameFromLeavesNothing() {
        val hint = folderHint(setOf("cl"), all, cameFrom = setOf("cl"))
        assertEquals(emptyList(), hint.folders)
    }

    @Test
    fun aPageOfSeveralFoldersLeavesOutEachOfThem() {
        val hint = folderHint(setOf("in", "cl", "fi"), all, cameFrom = setOf("in", "cl"))
        assertTrue(hint.also)
        assertEquals(listOf(financeInvoices), hint.folders)
    }

    @Test
    fun withoutContextShowsEveryFolderWithoutAlso() {
        val hint = folderHint(setOf("cl", "fi", "fr"), all, cameFrom = emptySet())
        assertFalse(hint.also)
        assertEquals(listOf(clients, financeInvoices, freelancing), hint.folders)
    }

    @Test
    fun leavesOutDraftsReadLaterAndUnknownFolders() {
        val hint = folderHint(setOf("dr", "rl", "gone", "ar"), all, cameFrom = emptySet())
        assertEquals(listOf(archive), hint.folders)
    }

    @Test
    fun stripsLeaveOutSentTrashAndSpamButTheDetailsListEveryFolder() {
        val sent = MessageFolder("se", listOf("Sent"), role = MailboxRole.Sent)
        val trash = MessageFolder("tr", listOf("Trash"), role = MailboxRole.Trash)
        val junk = MessageFolder("sp", listOf("Junk"), role = MailboxRole.Junk)
        val folders = all + listOf(sent, trash, junk).associateBy { it.id }
        val ids = setOf("se", "tr", "sp", "rl", "cl")
        assertEquals(listOf(clients), folderHint(ids, folders, cameFrom = emptySet()).folders)
        assertEquals(listOf(clients, junk, readLater, sent, trash), orderedFolders(ids, folders))
        assertTrue(inJunk(ids, folders))
        assertFalse(inJunk(setOf("cl"), folders))
    }

    @Test
    fun putsTheInboxFirstThenSortsByPath() {
        val hint = folderHint(setOf("fri", "ar", "in", "fi"), all, cameFrom = emptySet())
        assertEquals(listOf(inbox, archive, financeInvoices, freelancingInvoices), hint.folders)
    }

    @Test
    fun noFoldersMeansAnEmptyHint() =
        assertEquals(emptyList(), folderHint(emptySet(), all, cameFrom = setOf("in")).folders)

    @Test
    fun oneFolderShowsItsFullPath() =
        assertEquals(listOf(listOf("Finance", "Invoices")), listOf(financeInvoices).displayPaths(full = false))

    @Test
    fun severalFoldersShowTheirNames() =
        assertEquals(
            listOf(listOf("Invoices"), listOf("Freelancing")),
            listOf(financeInvoices, freelancing).displayPaths(full = false),
        )

    @Test
    fun clashingNamesShowTheirFullPaths() =
        assertEquals(
            listOf(listOf("Finance", "Invoices"), listOf("Freelancing", "Invoices"), listOf("Clients")),
            listOf(financeInvoices, freelancingInvoices, clients).displayPaths(full = false),
        )

    @Test
    fun clashesIgnoreCase() {
        val lower = MessageFolder("x", listOf("Work", "invoices"))
        assertEquals(
            listOf(listOf("Finance", "Invoices"), listOf("Work", "invoices")),
            listOf(financeInvoices, lower).displayPaths(full = false),
        )
    }

    @Test
    fun deeplyNestedClashesShowEveryLevel() {
        val deep = MessageFolder("d", listOf("Work", "Clients", "Acme", "2026", "Invoices"))
        assertEquals(
            listOf(
                listOf("Work", "Clients", "Acme", "2026", "Invoices"),
                listOf("Finance", "Invoices"),
                listOf("Clients"),
            ),
            listOf(deep, financeInvoices, clients).displayPaths(full = false),
        )
    }

    @Test
    fun fullShowsEveryPath() =
        assertEquals(
            listOf(listOf("Finance", "Invoices"), listOf("Freelancing")),
            listOf(financeInvoices, freelancing).displayPaths(full = true),
        )
}
