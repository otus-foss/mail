package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mail.INBOX_ROLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderPlacementTest {
    private fun folder(
        id: String,
        order: Long,
        parentId: String? = null,
        role: String? = null,
        rights: FolderRights = FolderRights(),
    ) = MailFolder("ada", id, id, parentId, role, order, rights = rights)

    private fun account(
        vararg folders: MailFolder,
        limits: FolderLimits = FolderLimits(),
    ) = AccountFolders("ada", "Ada", folders.toList(), limits, readLaterId = "later")

    private val spaced =
        account(
            folder("inbox", 0, role = INBOX_ROLE),
            folder("A", 10),
            folder("B", 20),
            folder("C", 30),
        )

    private fun AccountFolders.order(placement: FolderPlacement) =
        placed(placement).folderTree().filter { it.folder.isCustom }.map { it.folder.id }

    @Test
    fun aGapBetweenNeighborsRenumbersOnlyTheMovedFolder() {
        val placement = spaced.placement(spaced.folder("C")!!, null, 1)!!
        assertEquals(mapOf("C" to 15L), placement.sortOrders)
        assertEquals(listOf("A", "C", "B"), spaced.order(placement))
    }

    @Test
    fun theEndsStepPastTheFirstAndLastFolder() {
        assertEquals(mapOf("C" to 5L), spaced.placement(spaced.folder("C")!!, null, 0)!!.sortOrders)
        assertEquals(mapOf("A" to 40L), spaced.placement(spaced.folder("A")!!, null)!!.sortOrders)
    }

    @Test
    fun equalNumbersRenumberTheSiblingsAndSendOnlyChanges() {
        val account = account(folder("A", 0), folder("B", 0), folder("C", 0), folder("D", 20))
        val placement = account.placement(account.folder("D")!!, null, 1)!!
        // D's new number is the 20 it has, so it is not sent.
        assertEquals(mapOf("A" to 10L, "B" to 30L, "C" to 40L), placement.sortOrders)
        assertEquals(listOf("A", "D", "B", "C"), account.order(placement))
    }

    @Test
    fun droppingOntoAFolderNestsItLastAndKeepsAFittingNumber() {
        val account = account(folder("A", 10), folder("B", 20), folder("A1", 5, parentId = "A"))
        val placement = account.placement(account.folder("B")!!, "A")!!
        assertEquals("A", placement.parentId)
        assertEquals(emptyMap(), placement.sortOrders, "20 already sorts after A1's 5")
        assertEquals(listOf("A", "A1", "B"), account.order(placement))
    }

    @Test
    fun theSamePlaceSendsNothing() {
        assertNull(spaced.placement(spaced.folder("B")!!, null, 1))
    }

    @Test
    fun refusedPlacesAreNull() {
        val account =
            account(
                folder("inbox", 0, role = INBOX_ROLE),
                folder("later", 1),
                folder("A", 10),
                folder("A1", 10, parentId = "A"),
                folder("B", 20),
                folder("Locked", 30, rights = FolderRights(mayRename = false)),
                folder("Closed", 40, rights = FolderRights(mayCreateChild = false)),
                folder("z", 5, parentId = "B"),
                folder("Z", 50),
                limits = FolderLimits(maxDepth = 2),
            )
        val refused =
            listOf(
                "system folder" to account.placement(account.folder("inbox")!!, null, 3),
                "Read later" to account.placement(account.folder("later")!!, null, 3),
                "no rename right" to account.placement(account.folder("Locked")!!, null, 0),
                "into itself" to account.placement(account.folder("A")!!, "A1"),
                "too deep" to account.placement(account.folder("A1")!!, "z"),
                "no child right" to account.placement(account.folder("B")!!, "Closed"),
                "name taken" to account.placement(account.folder("Z")!!, "B"),
            )
        refused.forEach { (why, placement) -> assertNull(placement, why) }
    }

    @Test
    fun sharedMailboxesArePlacedInTheirOwnAccount() {
        val shared = SharedMailboxes("u2", "grace@example.org", listOf(folder("A", 10).copy(sharedIn = "u2")))
        val account = account(folder("A", 10)).copy(shared = listOf(shared))
        val folder = shared.folders.single()
        val placement = account.scopeOf(folder)!!.placement(folder, null, 0)
        assertNull(placement, "The only shared folder is already first")
        val moved = account.placed(FolderPlacement(folder, null, mapOf("A" to 3L)))
        assertEquals(10L, moved.folder("A")?.sortOrder, "The login's own A keeps its number")
        assertEquals(
            3L,
            moved.shared
                .single()
                .folders
                .single()
                .sortOrder,
        )
    }
}
