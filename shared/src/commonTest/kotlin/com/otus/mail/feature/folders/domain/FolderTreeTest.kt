package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FolderTreeTest {
    private fun folder(
        id: String,
        name: String = id,
        parentId: String? = null,
        role: String? = null,
        sortOrder: Long = 0,
    ) = MailFolder("ada", id, name, parentId, role, sortOrder)

    @Test
    fun systemFoldersComeFirstAndChildrenFollowTheirParent() {
        val folders =
            listOf(
                folder("t", "Trash", role = "trash"),
                folder("w", "work"),
                folder("r", "Receipts"),
                folder("a", "Amazon", parentId = "r"),
                folder("i", "Inbox", role = "inbox"),
                folder("s", "Sent", role = "sent"),
                folder("b", "Bank", parentId = "r", sortOrder = -1),
            )
        val tree = folderTree(folders)
        assertEquals(listOf("i", "s", "t", "r", "b", "a", "w"), tree.map { it.folder.id })
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 0), tree.map { it.depth })
        assertEquals("Receipts / Bank", tree.first { it.folder.id == "b" }.path)
    }

    @Test
    fun missingParentsAndCyclesStillShowEveryFolderOnce() {
        val folders =
            listOf(
                folder("o", "Orphan", parentId = "gone"),
                folder("x", "Loop A", parentId = "y"),
                folder("y", "Loop B", parentId = "x"),
                folder("self", "Self", parentId = "self"),
            )
        val tree = folderTree(folders)
        assertEquals(setOf("o", "x", "y", "self"), tree.map { it.folder.id }.toSet())
        assertEquals(4, tree.size)
        assertEquals(0, tree.first { it.folder.id == "o" }.depth)
    }

    @Test
    fun hiddenFoldersLeaveTheirChildrenInPlace() {
        val folders = listOf(folder("i", "Inbox", role = "inbox"), folder("c", "Clients", parentId = "i"))
        val tree = folderTree(folders) { it.role == INBOX_ROLE }
        assertEquals(listOf(FolderNode(folders[1], 0, "Clients")), tree)
    }

    @Test
    fun ancestorsAndSubtreesFollowParents() {
        val folders = listOf(folder("r"), folder("a", parentId = "r"), folder("b", parentId = "a"), folder("c"))
        assertEquals(2, ancestorCount(folders, folders[2]))
        assertEquals(setOf("r", "a", "b"), subtreeIds(folders, folders[0]))
    }
}
