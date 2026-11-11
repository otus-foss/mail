package com.otusfoss.mail.core.mailbox

import kotlin.test.Test
import kotlin.test.assertEquals

class FolderTreeTest {
    private fun folder(
        id: String,
        name: String = id,
        parentId: String? = null,
        role: MailboxRole? = null,
        sortOrder: Long = 0,
    ) = MailFolder("ada", id, name, parentId, role, sortOrder)

    @Test
    fun systemFoldersComeFirstAndChildrenFollowTheirParent() {
        val folders =
            listOf(
                folder("t", "Trash", role = MailboxRole.Trash),
                folder("w", "work"),
                folder("r", "Receipts"),
                folder("a", "Amazon", parentId = "r"),
                folder("i", "Inbox", role = MailboxRole.Inbox),
                folder("s", "Sent", role = MailboxRole.Sent),
                folder("b", "Bank", parentId = "r", sortOrder = -1),
            )
        val tree = folderTree(folders)
        assertEquals(listOf("i", "s", "t", "r", "b", "a", "w"), tree.map { it.folder.id })
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 0), tree.map { it.depth })
        assertEquals(listOf("Receipts", "Bank"), tree.first { it.folder.id == "b" }.path)
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
        val folders = listOf(folder("i", "Inbox", role = MailboxRole.Inbox), folder("c", "Clients", parentId = "i"))
        val tree = folderTree(folders) { it.role == MailboxRole.Inbox }
        assertEquals(listOf(FolderNode(folders[1], 0, listOf("Clients"))), tree)
    }

    @Test
    fun readLaterFollowsTheInboxAndIsMarked() {
        val folders =
            listOf(
                folder("s", "Sent", role = MailboxRole.Sent),
                folder("a", "Amazon"),
                folder("l", "Someday"),
                folder("i", "Inbox", role = MailboxRole.Inbox),
            )
        val tree = folderTree(folders, readLaterId = "l")
        assertEquals(listOf("i", "l", "s", "a"), tree.map { it.folder.id })
        assertEquals(listOf("l"), tree.filter { it.readLater }.map { it.folder.id })
    }

    @Test
    fun readLaterTreeNestsItsSubfoldersUnderItWhereverItSits() {
        val folders =
            listOf(
                folder("i", "Inbox", role = MailboxRole.Inbox),
                folder("p", "Projects"),
                folder("l", "Read later", parentId = "p"),
                folder("a", "Articles", parentId = "l"),
            )
        val tree = AccountFolders("ada", "Ada", folders, readLaterId = "l").readLaterTree()
        assertEquals(listOf("l" to 0, "a" to 1), tree.map { it.folder.id to it.depth })
        assertEquals(listOf("Read later", "Articles"), tree.last().path)
        assertEquals(emptyList(), AccountFolders("ada", "Ada", folders).readLaterTree())
    }

    @Test
    fun ancestorsAndSubtreesFollowParents() {
        val folders = listOf(folder("r"), folder("a", parentId = "r"), folder("b", parentId = "a"), folder("c"))
        assertEquals(listOf("a", "r"), ancestors(folders, folders[2]).map { it.id })
        assertEquals(2, ancestorCount(folders, folders[2]))
        assertEquals(setOf("r", "a", "b"), subtreeIds(folders, folders[0]))
        val loop = listOf(folder("x", parentId = "y"), folder("y", parentId = "x"))
        assertEquals(listOf("y"), ancestors(loop, loop[0]).map { it.id })
    }
}
