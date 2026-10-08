package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderChangesTest {
    private val receipts = MailFolder("ada", "r", "Receipts")
    private val amazon = MailFolder("ada", "a", "Amazon", parentId = "r")
    private val work = MailFolder("ada", "w", "Work")
    private val locked = MailFolder("ada", "l", "Shared", rights = FolderRights(mayCreateChild = false))

    private fun account(limits: FolderLimits = FolderLimits()) =
        AccountFolders("ada", "Ada", listOf(receipts, amazon, work, locked), limits)

    @Test
    fun namesAreTrimmedAndUniqueAmongSiblingsWithoutCase() {
        val account = account(FolderLimits(maxNameBytes = 8))
        assertEquals(FolderNameProblem.Empty, folderNameProblem("   ", null, account))
        assertEquals(FolderNameProblem.Taken, folderNameProblem(" work ", null, account))
        assertNull(folderNameProblem("Work", "r", account), "The same name under another parent is fine")
        assertEquals(FolderNameProblem.TooLong, folderNameProblem("Überweisung", null, account))
        assertNull(folderNameProblem("WORK", null, account, renaming = work), "Renaming may change case")
    }

    @Test
    fun foldersMoveOnlyWhereTheyFit() {
        val targets = moveTargets(account(), receipts).map { it?.id }
        assertEquals(listOf(null, "w"), targets, "Never into itself, its subfolders, or a folder without children")
        val shallow = moveTargets(account(FolderLimits(maxDepth = 2, mayCreateTopLevel = false)), receipts)
        assertTrue(shallow.isEmpty(), "Receipts has a subfolder, so it cannot nest two levels deep")
        assertEquals(listOf("r"), moveTargets(account(FolderLimits(maxDepth = 2)), work).mapNotNull { it?.id })
    }

    @Test
    fun creationRespectsRightsAndDepth() {
        assertTrue(canCreateIn(account(), null))
        assertFalse(canCreateIn(account(FolderLimits(mayCreateTopLevel = false)), null))
        assertFalse(canCreateIn(account(), locked))
        assertTrue(canCreateIn(account(FolderLimits(maxDepth = 3)), amazon))
        assertFalse(canCreateIn(account(FolderLimits(maxDepth = 2)), amazon))
    }
}
