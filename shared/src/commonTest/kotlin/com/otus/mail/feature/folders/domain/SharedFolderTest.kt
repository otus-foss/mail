package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.TRASH_ROLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedFolderTest {
    private val ada =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = INBOX_ROLE),
                MailFolder("ada", "c", "Clients"),
                MailFolder("ada", "a", "Acme", parentId = "c", unread = 2),
                MailFolder("ada", "p", "Projects", parentId = "i"),
                MailFolder("ada", "t", "Trash", role = TRASH_ROLE),
                MailFolder("ada", "o", "Old", parentId = "t"),
                MailFolder("ada", "l", "Read later"),
                MailFolder("ada", "x", "Articles", parentId = "l"),
            ),
            readLaterId = "l",
        )
    private val grace =
        AccountFolders(
            "grace",
            "Grace",
            listOf(
                MailFolder("grace", "c", "clients"),
                MailFolder("grace", "g", "Globex", parentId = "c", unread = 1),
                MailFolder("grace", "a", "ACME", parentId = "c", unread = 3),
                MailFolder("grace", "r", "Receipts"),
            ),
        )

    private fun List<SharedFolder>.outline(): List<String> =
        flatMap { it.flatten() }.map { folder ->
            "  ".repeat(folder.key.size - 1) + folder.name +
                folder.folders.joinToString(",", " [", "]") { "${it.accountId}/${it.id}" }
        }

    @Test
    fun foldersWithTheSamePathInAnyCaseAreOneFolderInFirstSeenOrder() {
        assertEquals(
            listOf(
                "Projects [ada/p]",
                "Articles [ada/x]",
                "Clients [ada/c,grace/c]",
                "  Acme [ada/a,grace/a]",
                "  Globex [grace/g]",
                "Receipts [grace/r]",
            ),
            sharedFolders(listOf(ada, grace)).outline(),
            "System folders and Read later make way for their subfolders; Trash keeps its own",
        )
    }

    @Test
    fun aFolderIsFoundByItsLowerCasePath() {
        val folders = sharedFolders(listOf(ada, grace))
        assertEquals(
            listOf("ada/a", "grace/a"),
            sharedFolder(folders, listOf("clients", "acme"))?.folders?.map {
                "${it.accountId}/${it.id}"
            },
        )
        assertNull(sharedFolder(folders, listOf("old")))
    }
}
