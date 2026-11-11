package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.mail.core.mailbox.FolderKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderLabelsTest {
    @Test
    fun uniqueNamesShowAlone() {
        val labels = folderLabels(listOf(account("ada", folder("ada", "1", "Clients"), folder("ada", "2", "Travel"))))
        assertEquals(FolderLabel("Clients", spoken = "Clients"), labels[FolderKey("ada", "1")])
    }

    @Test
    fun sameNamesGetTheFewestParentsThatTellThemApart() {
        val labels =
            folderLabels(
                listOf(
                    account(
                        "ada",
                        folder("ada", "a", "a"),
                        folder("ada", "ab", "b", "a"),
                        folder("ada", "c", "c"),
                        folder("ada", "cb", "b", "c"),
                        folder("ada", "b", "b"),
                    ),
                ),
            )
        assertEquals("a", labels.getValue(FolderKey("ada", "ab")).prefix)
        assertEquals("c", labels.getValue(FolderKey("ada", "cb")).prefix)
        assertNull(labels.getValue(FolderKey("ada", "b")).prefix, "The top-level b is told apart by having no parent")
        assertEquals("a, b", labels.getValue(FolderKey("ada", "ab")).spoken)
    }

    @Test
    fun deepPathsFoldTheMiddleParents() {
        val labels =
            folderLabels(
                listOf(
                    account(
                        "ada",
                        folder("ada", "1", "Invoices", "Work", "x", "y", "z"),
                        folder("ada", "2", "Invoices", "Home", "x", "y", "z"),
                    ),
                ),
            )
        assertEquals("Work / …", labels.getValue(FolderKey("ada", "1")).prefix)
        assertEquals("Work, x, y, z, Invoices", labels.getValue(FolderKey("ada", "1")).spoken)
    }

    @Test
    fun theSamePathInTwoAccountsShowsTheAccount() {
        val labels =
            folderLabels(
                listOf(account("ada", folder("ada", "1", "Clients")), account("bo", folder("bo", "1", "clients"))),
            )
        val ada = labels.getValue(FolderKey("ada", "1"))
        assertTrue(ada.showAccount)
        assertEquals("Clients, ada", ada.spoken)
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
