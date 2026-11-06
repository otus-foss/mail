package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.testing.STALWART_SESSION
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageFilingRollbackTest {
    private val account =
        AccountFolders(
            "ada",
            "Ada",
            listOf(MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox), MailFolder("ada", "l", "Read later")),
            readLaterId = "l",
        )

    @Test
    fun rejectedMovesRestoreOnlyTheScopeWhereTheyStarted() =
        runTest {
            rejectedAction(readLater = false, switchFolder = false)
            rejectedAction(readLater = false, switchFolder = true)
        }

    @Test
    fun rejectedReadLaterRemovalsRestoreOnlyTheScopeWhereTheyStarted() =
        runTest {
            rejectedAction(readLater = true, switchFolder = false)
            rejectedAction(readLater = true, switchFolder = true)
        }

    private suspend fun TestScope.rejectedAction(
        readLater: Boolean,
        switchFolder: Boolean,
    ) {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val engine =
            MockEngine { request ->
                val text =
                    if (request.method == HttpMethod.Get) {
                        STALWART_SESSION
                    } else {
                        started.complete(Unit)
                        finish.await()
                        """{"methodResponses": [["Email/set", {"oldState": "e", "newState": "e",
                    "notUpdated": {"m": {"type": "forbidden"}}}, "c0"]]}"""
                    }
                respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val store =
            InMemoryLoginStore().apply {
                save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            }
        val services = MailServices(logins = store, createClient = { JmapClient(engine) })
        val folders = testFolders(services) { listOf(account) }
        folders.refresh()
        runCurrent()
        val initialScope = if (readLater) MailScope.Folder("ada", "l") else MailScope.Inbox
        val initial = message("original", if (readLater) "l" else "i")
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope) =
                    InboxSnapshot(listOf(if (scope == initialScope) initial else message("other", "r")))
            }
        val feed = InboxFeedState(source, this, defaultScope = initialScope)
        feed.refresh()
        runCurrent()
        val action =
            async {
                coroutineScope {
                    val edits = testEdits(services, feed)
                    val filing = MessageFiling(services.senderHistory, folders, FolderColorsState(), edits)
                    if (readLater) {
                        assertNotNull(filing.readLater(feed, initial)).apply()
                    } else {
                        filing.move(feed, initial, MoveTarget("r", listOf("Receipts")))
                    }
                }
            }
        started.await()
        assertTrue(feed.messages.isEmpty())
        if (switchFolder) {
            feed.show(MailScope.Folder("ada", "r"))
            runCurrent()
        }
        finish.complete(Unit)
        action.await()
        assertEquals(listOf(if (switchFolder) "other" else "original"), feed.messages.map { it.id })
        assertNotNull(feed.notice)
    }

    private fun message(
        id: String,
        mailbox: String,
    ) = MailMessage(
        id,
        MailSender("Ada", "ada@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Mailbox", "Ada", "ada", mailboxId = mailbox, mailboxIds = setOf("i", "l")),
        emailId = "m",
    )
}
