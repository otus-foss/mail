package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderRights
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.SharedMailboxes
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedMailboxesTest {
    private val reviews =
        MailFolder("otus", "r", "Design reviews", rights = FolderRights(mayAddItems = false), sharedIn = "f1")
    private val shared = SharedMailboxes("f1", "florian@rubylabs.design", listOf(reviews))
    private val viewer = AccountFolders("otus", "otus@rubylabs.design", shared = listOf(shared))

    @Test
    fun aMailboxFromAConnectedOwnerShowsOnceUnderTheOwner() {
        val owner =
            AccountFolders(
                "florian",
                "Florian",
                listOf(MailFolder("florian", "r", "Design reviews")),
                mailboxOwner = "florian@rubylabs.design",
            )
        val placement = sharedPlacements(listOf(viewer, owner)).single()
        assertEquals("florian", placement.accountId)
        assertEquals(null, placement.folder.sharedIn, "The owner's own folder is listed")
        assertEquals(
            SharedBy("florian@rubylabs.design", canEdit = false),
            placement.sharedBy,
            "Named as shared by its owner",
        )
    }

    @Test
    fun aMailboxFromAnUnknownOwnerShowsUnderTheLoginThatSeesIt() {
        val stranger = AccountFolders("x", "x@example.org", mailboxOwner = "x@example.org")
        val placement = sharedPlacements(listOf(viewer, stranger)).single()
        assertEquals("otus" to "florian@rubylabs.design", placement.accountId to placement.sharedBy.owner)
        assertEquals(reviews, placement.folder)
    }

    @Test
    fun anOwnerWithoutThatMailboxLeavesItUnderTheLogin() {
        val owner = AccountFolders("florian", "florian@rubylabs.design")
        assertEquals("otus", sharedPlacements(listOf(viewer, owner)).single().accountId)
    }
}
