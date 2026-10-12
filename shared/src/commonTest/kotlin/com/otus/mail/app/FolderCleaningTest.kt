package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.FolderCleanup
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderCleaningTest {
    private val locked = FolderRights(mayRemoveItems = false)
    private val account =
        AccountFolders(
            "ada",
            "Ada",
            listOf(
                MailFolder("ada", "i", "Inbox", role = "inbox"),
                MailFolder("ada", "j", "Junk Mail", role = "junk", total = 4),
                MailFolder("ada", "t", "Deleted", role = "trash", total = 2, rights = locked),
                MailFolder("ada", "s", "Sent", role = "sent", total = 9),
            ),
        )
    private val sent = mutableListOf<String>()
    private var reply: (String) -> String = { DESTROYED_M1 }

    private fun message(
        id: String,
        folder: String,
    ) = MailMessage(
        id,
        MailSender("Grace", "grace@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Mailbox", "Ada", "ada", mailboxId = folder, mailboxIds = setOf(folder)),
        emailId = id,
    )

    /** The cleanup of a feed that shows [scope], holding [messages], and the feed. */
    private fun TestScope.cleanup(
        scope: MailScope,
        vararg messages: MailMessage,
    ): Pair<FolderCleanup, InboxFeedState> {
        val store = InMemoryLoginStore().apply { save(JmapLogin("ada", URL, "ada@example.org", "Ada"), "secret") }
        val engine =
            stalwartLike { body ->
                sent += body
                reply(body)
            }
        val services = MailServices(logins = store, createClient = { JmapClient(engine) })
        val folders = FoldersState(services.folders, this, loadAccounts = { listOf(account) })
        folders.refresh()
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope) = InboxSnapshot(messages.toList())
            }
        val feed = InboxFeedState(source, this, defaultScope = scope)
        feed.refresh()
        runCurrent()
        return FolderCleaning(services, folders, this).cleanup(feed) to feed
    }

    @Test
    fun deletingNeedsSpamOrTrashThatLetsMessagesLeave() =
        runTest {
            val (cleanup) = cleanup(MailScope.Inbox)
            assertTrue(cleanup.canDelete(message("m1", "j")))
            assertFalse(cleanup.canDelete(message("m1", "t")), "Trash here keeps its messages")
            assertFalse(cleanup.canDelete(message("m1", "i")), "The inbox has no Delete forever")
            assertFalse(cleanup.canDelete(message("m1", "j").copy(emailId = "")), "Supplied mail has no server id")
            assertEquals(FolderRole.Junk, cleanup.role(message("m1", "j")))
            assertNull(cleanup.role(message("m1", "i")))
        }

    @Test
    fun onlySpamAndTrashListsThatAllowItCanBeEmptied() =
        runTest {
            val (spam) = cleanup(MailScope.Role("junk", "Spam"))
            assertEquals(FolderRole.Junk, spam.listRole())
            val emptying = assertNotNull(spam.emptying())
            assertEquals(4, emptying.count)
            assertNull(cleanup(MailScope.Folder("ada", "t")).first.emptying(), "Trash here keeps its messages")
            val (sentList) = cleanup(MailScope.Folder("ada", "s"))
            assertEquals(FolderRole.Sent, sentList.listRole())
            assertNull(sentList.emptying())
            assertNull(cleanup(MailScope.Inbox).first.listRole())
        }

    @Test
    fun deletedMessagesLeaveAndKeptOnesComeBackWithANotice() =
        runTest {
            val (cleanup, feed) = cleanup(MailScope.Role("junk", "Spam"), message("m1", "j"), message("m2", "j"))
            reply = {
                """{"methodResponses": [["Email/set", {"newState": "e2", "destroyed": ["m1"],
                    "notDestroyed": {"m2": {"type": "forbidden"}}}, "c0"]]}"""
            }
            cleanup.onDelete(feed.messages)
            assertTrue(feed.messages.isEmpty(), "Both leave at once")
            settle { feed.notice != null }
            assertEquals(listOf("m2"), feed.messages.map { it.id })
            assertEquals("Couldn't delete 1 message. Try again.", feed.notice)
            assertTrue(sent.any { "\"destroy\":[\"m1\",\"m2\"]" in it }, sent.toString())
        }

    @Test
    fun emptyingTakesTheLoadedMessagesOffAndDeletesTheFolder() =
        runTest {
            val (cleanup, feed) = cleanup(MailScope.Role("junk", "Spam"), message("m1", "j"))
            reply = { body ->
                when {
                    "Email/set" in body -> DESTROYED_M1
                    "\"position\":0" in body -> page("\"m1\"", """{"id": "m1", "mailboxIds": {"j": true}}""")
                    else -> page("", "")
                }
            }
            assertNotNull(cleanup.emptying()).run()
            assertTrue(feed.messages.isEmpty())
            settle { sent.any { "Email/set" in it } }
            // A refused deletion would bring the message back; give it the chance.
            settle { false }
            assertTrue(feed.messages.isEmpty())
            assertNull(feed.notice)
            assertTrue(sent.any { "\"destroy\":[\"m1\"]" in it }, sent.toString())
        }

    private fun page(
        ids: String,
        list: String,
    ) = """{"methodResponses": [["Email/query", {"ids": [$ids]}, "c0"],
        ["Email/get", {"state": "e", "list": [$list]}, "c1"]]}"""

    /** Lets the JMAP calls, which read logins off the test thread, finish until [done], or for a short while. */
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(SETTLE_STEPS) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private companion object {
        const val URL = "https://mail.example.org/.well-known/jmap"
        const val SETTLE_STEPS = 50
        const val DESTROYED_M1 =
            """{"methodResponses": [["Email/set", {"newState": "e2", "destroyed": ["m1"]}, "c0"]]}"""
    }
}
