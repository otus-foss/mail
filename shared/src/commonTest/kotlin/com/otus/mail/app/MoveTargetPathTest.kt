package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.MoveTarget
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Move targets carry the shared folder path a Move to button saves, and the folder's color. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveTargetPathTest {
    private val account =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = "inbox"),
                MailFolder("ada", "c", "Clients"),
                MailFolder("ada", "a", "Acme", parentId = "c"),
                MailFolder("ada", "n", "Newsletters", parentId = "i"),
                MailFolder("ada", "t", "Trash", role = "trash"),
                MailFolder("ada", "o", "Old", parentId = "t"),
            ),
        )

    private val services =
        MailServices(
            logins = InMemoryLoginStore(),
            createClient = { JmapClient(MockEngine { error("No calls") }) },
        )

    /** The message's move targets after every folder that can have a color is given [color]. */
    private fun TestScope.targets(color: String? = null): List<MoveTarget> {
        val folders = FoldersState(services.folders, this, loadAccounts = { listOf(account) })
        folders.refresh()
        runCurrent()
        val colors = FolderColorsState(accounts = { folders.accounts }).apply { setColor(account.folders, color) }
        return MessageFiling(services, folders, colors, this).moveTargets(message())
    }

    @Test
    fun ownFoldersArePathedAsSharedFoldersAreAndOthersHaveNone() =
        runTest {
            val paths = targets().associate { it.name to it.path }
            assertEquals(listOf("Clients", "Acme"), paths["Acme"])
            // A folder under the inbox counts from the top, as system folders are left out of shared paths.
            assertEquals(listOf("Newsletters"), paths["Newsletters"])
            assertEquals(null, paths["Trash"])
            assertEquals(null, paths["Old"])
        }

    @Test
    fun ownFoldersCarryTheirColorAndSystemFoldersNone() =
        runTest {
            val colors = targets("Teal").associate { it.name to it.color }
            assertEquals("Teal", colors["Acme"])
            assertEquals(null, colors["Trash"])
        }

    private fun message() =
        MailMessage(
            "1",
            MailSender("Grace", "grace@example.org"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-01", "12:00"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "i", mailboxIds = setOf("i")),
            emailId = "m",
        )
}
