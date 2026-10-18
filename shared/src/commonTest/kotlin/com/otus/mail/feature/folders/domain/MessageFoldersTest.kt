package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageFoldersTest {
    private val inbox = MailFolder("ada", "i", "Inbox", role = INBOX_ROLE)
    private val sent = MailFolder("ada", "s", "Sent", role = "sent")
    private val later = MailFolder("ada", "l", "Read later")
    private val receipts = MailFolder("ada", "r", "Receipts")

    private fun account(
        maxFoldersPerMessage: Int? = null,
        readLater: MailFolder? = later,
    ) = AccountFolders(
        "ada",
        "Ada",
        listOfNotNull(inbox, sent, readLater, receipts),
        FolderLimits(maxFoldersPerMessage = maxFoldersPerMessage),
        readLaterId = readLater?.id,
    )

    @Test
    fun aMessageIsSavedInPlaceAndRemovedWhileAnotherFolderKeepsIt() {
        assertEquals(ReadLaterStep.Save, account().readLaterStep(setOf("i"), shownId = "i"))
        assertEquals(ReadLaterStep.Remove, account().readLaterStep(setOf("i", "l"), shownId = "i"))
        assertEquals(ReadLaterStep.Remove, account().readLaterStep(setOf("i", "l"), shownId = "l"))
        assertNull(account().readLaterStep(setOf("l"), shownId = "l"), "A message must stay in one folder")
    }

    @Test
    fun aServerAllowingOneFolderPerMessageMovesItInstead() {
        assertEquals(ReadLaterStep.Move, account(maxFoldersPerMessage = 1).readLaterStep(setOf("i"), shownId = "i"))
        assertEquals(ReadLaterStep.Save, account(maxFoldersPerMessage = 2).readLaterStep(setOf("i"), shownId = "i"))
    }

    @Test
    fun nothingIsOfferedWithoutTheFolderOrItsRights() {
        assertNull(account(readLater = null).readLaterStep(setOf("i"), shownId = "i"))
        val locked = later.copy(rights = FolderRights(mayAddItems = false, mayRemoveItems = false))
        assertNull(account(readLater = locked).readLaterStep(setOf("i"), shownId = "i"))
        assertNull(account(readLater = locked).readLaterStep(setOf("i", "l"), shownId = "i"))
    }

    @Test
    fun rowsNameTheInboxAndTheUsersFoldersExceptTheOneShown() {
        val labels = account().folderTree().labelFolders(setOf("i", "s", "l", "r"), shownId = "r")
        assertEquals(listOf("Inbox", "Read later"), labels.map { it.folder.name })
        assertEquals(listOf(false, true), labels.map { it.readLater })
        assertEquals(
            listOf("Read later", "Receipts"),
            account().folderTree().labelFolders(setOf("i", "l", "r"), "i").map { it.folder.name },
        )
    }
}
