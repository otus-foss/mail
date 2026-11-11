package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.core.designsystem.shared.reorder.components.TreeDropTarget
import com.otusfoss.core.designsystem.shared.reorder.components.TreeDropTarget.Before
import com.otusfoss.core.designsystem.shared.reorder.components.TreeDropTarget.Into
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.feature.folders.domain.placeableChildren
import com.otusfoss.mail.feature.folders.domain.placed
import com.otusfoss.mail.feature.folders.domain.placement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FolderTreeDropTest {
    private fun folder(
        id: String,
        order: Long,
        parentId: String? = null,
        name: String = id,
        role: MailboxRole? = null,
    ) = MailFolder("ada", id, name, parentId, role, order)

    private fun account(vararg folders: MailFolder) = AccountFolders("ada", "Ada", folders.toList())

    // Rows: A, A1 and A2 inside A, B, C.
    private val ada =
        account(
            folder("inbox", 0, role = MailboxRole.Inbox),
            folder("A", 10),
            folder("A1", 10, parentId = "A"),
            folder("A2", 20, parentId = "A"),
            folder("B", 20),
            folder("C", 30),
        )

    private fun AccountFolders.rows() = folderRows(emptyList(), allAccounts = false)

    private fun AccountFolders.drop(
        from: String,
        target: TreeDropTarget,
        rows: List<FolderListRow> = rows(),
    ): FolderDrop? =
        rows.treeDrop(rows.indexOfFirst { it.folder.id == from }, target) { _, parentId -> placeableChildren(parentId) }

    private fun AccountFolders.placementOf(drop: FolderDrop?) =
        drop?.let { placement(it.folder, it.parentId, it.index) }

    private fun AccountFolders.orderAfter(drop: FolderDrop?): List<Pair<String, String?>> =
        placed(assertNotNull(placementOf(drop))).rows().map { it.folder.id to it.folder.parentId }

    @Test
    fun aGapPlacesTheFolderRightBeforeTheRowBelowAmongItsSiblings() {
        val drop = ada.drop("C", Before(1))
        assertEquals(FolderDrop(ada.folder("C")!!, "A", 0), drop)
        assertEquals(listOf("A" to null, "C" to "A", "A1" to "A", "A2" to "A", "B" to null), ada.orderAfter(drop))

        assertEquals(FolderDrop(ada.folder("A1")!!, null, 1), ada.drop("A1", Before(3)))
    }

    @Test
    fun theEndPlacesTheFolderAfterTheLastRowAmongItsSiblings() {
        val drop = ada.drop("A1", Before(5))
        assertEquals(FolderDrop(ada.folder("A1")!!, null, 3), drop)
        assertEquals("A1" to null, ada.orderAfter(drop).last())
    }

    @Test
    fun ontoARowMakesTheFolderItsFirstSubfolder() {
        val drop = ada.drop("C", Into(0))
        assertEquals(FolderDrop(ada.folder("C")!!, "A", 0), drop)
        assertEquals(listOf("A" to null, "C" to "A", "A1" to "A"), ada.orderAfter(drop).take(3))
        assertEquals(FolderDrop(ada.folder("C")!!, "B", 0), ada.drop("C", Into(3)))
    }

    @Test
    fun aDropIntoTheFolderItselfOrItsSubfoldersIsRefused() {
        assertNull(ada.placementOf(ada.drop("A", Into(1))))
        assertNull(ada.placementOf(ada.drop("A", Before(2))))
    }

    @Test
    fun aDropTooDeepOrBesideASiblingWithTheSameNameIsRefused() {
        val deep =
            account(
                folder("L1", 10),
                folder("L2", 10, parentId = "L1"),
                folder("L3", 10, parentId = "L2"),
                folder("L4", 10, parentId = "L3"),
                folder("L5", 10, parentId = "L4"),
                folder("C", 20),
            )
        assertNull(deep.placementOf(deep.drop("C", Into(4))))
        assertNotNull(deep.placementOf(deep.drop("C", Into(3))))

        val clash = account(folder("A", 10), folder("A1", 10, parentId = "A"), folder("D", 20, name = "a1"))
        assertNull(clash.placementOf(clash.drop("D", Into(0))))
    }

    @Test
    fun aDropInAnotherAccountIsRefused() {
        val grace = AccountFolders("grace", "Grace", listOf(MailFolder("grace", "G", "G", sortOrder = 10)))
        val rows = ada.rows() + grace.rows()
        assertNull(ada.drop("C", Into(rows.lastIndex), rows))
        assertNull(ada.drop("C", Before(rows.size), rows))
    }
}
