package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderParentsTest {
    private val clients = MailFolder("ada", "c", "Clients")
    private val acme = MailFolder("ada", "a", "Acme", parentId = "c")
    private val otherAcme = MailFolder("ada", "o", "Acme")
    private val travel = MailFolder("ada", "t", "Travel")
    private val inbox = MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox)
    private val account = AccountFolders("ada", "Ada", listOf(inbox, clients, acme, otherAcme, travel))

    @Test
    fun aFolderMovesToTheTopOrIntoOwnFoldersButNotItselfOrOneWithItsName() {
        assertEquals(listOf(null, "o", "c", "a"), account.parentChoices(travel).map { it?.id })
        // Clients already holds an Acme, so the top-level Acme cannot move there.
        assertEquals(listOf(null, "a", "t"), account.parentChoices(otherAcme).map { it?.id })
    }

    @Test
    fun aPathNamesEveryLevelFromTheTop() {
        assertEquals("Clients / Acme", account.folderPath(acme))
        assertEquals("Travel", account.folderPath(travel))
    }
}
