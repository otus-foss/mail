package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.testing.testEdits
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
                MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox),
                MailFolder("ada", "c", "Clients"),
                MailFolder("ada", "a", "Acme", parentId = "c"),
                MailFolder("ada", "n", "Newsletters", parentId = "i"),
                MailFolder("ada", "t", "Trash", role = MailboxRole.Trash),
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
        val folders =
            FoldersState(services.folders, this, loadCached = { emptyList() }, loadAccounts = { listOf(account) })
        folders.refresh()
        runCurrent()
        val colors = FolderColorsState(accounts = { folders.accounts }).apply { setColor(account.folders, color) }
        return MessageFiling(services.senderHistory, folders, colors, testEdits(services)).moveTargets(message())
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
            MailLocation(
                "Inbox",
                "Ada",
                "ada",
                mailboxId = "i",
                mailboxIds = setOf("i"),
                mailboxRole = MailboxRole.Inbox,
            ),
            emailId = "m",
        )
}
