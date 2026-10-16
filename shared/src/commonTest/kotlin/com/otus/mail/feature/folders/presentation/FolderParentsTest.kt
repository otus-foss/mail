package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderParentsTest {
    private val clients = MailFolder("ada", "c", "Clients")
    private val acme = MailFolder("ada", "a", "Acme", parentId = "c")
    private val otherAcme = MailFolder("ada", "o", "Acme")
    private val travel = MailFolder("ada", "t", "Travel")
    private val account = AccountFolders("ada", "Ada", listOf(clients, acme, otherAcme, travel))

    @Test
    fun aFolderCanMoveToTheTopOrIntoAnyFolderButItselfOrOneWithItsName() {
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
