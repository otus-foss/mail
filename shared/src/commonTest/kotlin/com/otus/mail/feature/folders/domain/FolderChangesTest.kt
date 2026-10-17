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

    @Test
    fun otusNestsFiveLevelsAtMostEvenWhenTheServerAllowsMore() {
        // Level 1 to 5: l1 › l2 › l3 › l4 › l5, and a top-level folder with one subfolder.
        val chain =
            (1..5).map {
                MailFolder(
                    "ada",
                    "l$it",
                    "Level $it",
                    parentId =
                        if (it ==
                            1
                        ) {
                            null
                        } else {
                            "l${it - 1}"
                        },
                )
            }
        val branch = listOf(MailFolder("ada", "b", "Branch"), MailFolder("ada", "c", "Child", parentId = "b"))
        val deep = AccountFolders("ada", "Ada", chain + branch, FolderLimits(maxDepth = 10))
        assertEquals(5, FolderLimits().levels)
        assertEquals(3, FolderLimits(maxDepth = 3).levels, "A stricter server limit wins")
        assertTrue(canCreateIn(deep, chain[3]), "A fifth level is fine")
        assertFalse(canCreateIn(deep, chain[4]), "A sixth level is refused")
        val targets = moveTargets(deep, branch[0]).mapNotNull { it?.id }
        assertTrue("l3" in targets, "Branch › Child under level 3 ends at level 5")
        assertFalse("l4" in targets, "Under level 4 the child would sit at level 6")
    }
}
