package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.FolderCheck
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FolderChecksTest {
    @Test
    fun checkingAParentIncludesItsSubfolders() {
        val parent = folder("ada", "p", "Clients", descendants = setOf("c"))
        val child = folder("ada", "c", "Acme", "Clients", parentId = "p")
        val options = FilterOptions(accounts = listOf(account("ada", parent, child)))
        val withChild = InboxFilters().toggle(child)
        val withParent = withChild.toggle(parent)
        assertEquals(
            setOf(FolderRef("ada", "p", includeSubfolders = true)),
            withParent.folders,
            "The parent covers the child",
        )
        assertEquals(FolderCheck.On, withParent.check(parent, options))
        assertEquals(FolderCheck.Included, withParent.check(child, options))
        val only = withParent.includeSubfolders(parent, include = false)
        assertEquals(FolderCheck.Off, only.check(child, options))
        assertEquals(1, options.includedCount(FolderRef("ada", "p")))
        assertFalse(
            InboxFilters()
                .toggle(parent)
                .toggle(parent)
                .folders
                .isNotEmpty(),
        )
    }

    @Test
    fun selectedFoldersOutsideTheShortcutsFollowThem() {
        val folders = (1..8).map { folder("ada", "$it", "F$it") }
        val options =
            FilterOptions(
                accounts = listOf(account("ada", *folders.toTypedArray())),
                shortcuts = folders.map { it.key },
            )
        val chips = options.folderChips(InboxFilters(folders = setOf(FolderRef("ada", "8"))))
        assertEquals(listOf("F1", "F2", "F3", "F4", "F5", "F6", "F8"), chips.map { it.name })
    }

    @Test
    fun collapsedParentsHideTheirSubfoldersAndSearchFlattens() {
        val account =
            account(
                "ada",
                folder("ada", "p", "Clients", descendants = setOf("c", "d")),
                folder("ada", "c", "Acme", "Clients", parentId = "p", descendants = setOf("d")),
                folder("ada", "d", "Invoices", "Clients", "Acme", parentId = "c"),
            )
        assertEquals(listOf("Clients"), account.shownFolders("", setOf(FolderKey("ada", "p"))).map { it.name })
        assertEquals(listOf("Clients", "Acme"), account.shownFolders("", setOf(FolderKey("ada", "c"))).map { it.name })
        assertEquals(listOf("Invoices"), account.shownFolders(" inv ", setOf(FolderKey("ada", "p"))).map { it.name })
    }

    private fun account(
        id: String,
        vararg folders: FilterFolder,
    ) = FilterAccount(id, id, ChipAvatar("A", 0), folders.toList())

    private fun folder(
        login: String,
        id: String,
        name: String,
        vararg ancestors: String,
        parentId: String? = null,
        descendants: Set<String> = emptySet(),
    ) = FilterFolder(
        FolderKey(login, id),
        name,
        ancestors.toList(),
        Res.drawable.symbol_folder,
        parentId = parentId,
        descendants = descendants,
    )
}
