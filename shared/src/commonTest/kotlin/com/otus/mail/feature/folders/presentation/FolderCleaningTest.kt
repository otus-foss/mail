package com.otus.mail.feature.folders.presentation

import com.otus.mail.app.adaServices
import com.otus.mail.app.settle
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderRights
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.ThreadMember
import com.otus.mail.core.messages.presentation.FolderCleanup
import com.otus.mail.feature.inbox.data.conversationOf
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                MailFolder("ada", "i", "Inbox", role = MailboxRole.Inbox),
                MailFolder("ada", "j", "Junk Mail", role = MailboxRole.Junk, total = 4),
                MailFolder("ada", "t", "Deleted", role = MailboxRole.Trash, total = 2, rights = locked),
                MailFolder("ada", "s", "Sent", role = MailboxRole.Sent, total = 9),
            ),
        )
    private val sent = MutableStateFlow<List<String>>(emptyList())
    private val removed = MutableStateFlow<Set<String>>(emptySet())
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
        val engine =
            stalwartLike { body ->
                sent.update { it + body }
                val membershipRead = "Email/get" in body && "Email/query" !in body
                val response = if (membershipRead) membership(body, messages.toList()) else reply(body)
                val destroyed =
                    Json
                        .parseToJsonElement(response)
                        .jsonObject
                        .getValue("methodResponses")
                        .jsonArray
                        .flatMap {
                            it.jsonArray[1]
                                .jsonObject["destroyed"]
                                ?.jsonArray
                                .orEmpty()
                        }.map { it.jsonPrimitive.content }
                removed.update { it + destroyed }
                response
            }
        val services = adaServices(engine)
        val folders = testFolders(services) { listOf(account) }
        folders.refresh()
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope) =
                    InboxSnapshot(
                        messages.filterNot {
                            it.emailId in
                                removed.value
                        },
                    )
            }
        val feed = InboxFeedState(source, this, defaultScope = scope)
        feed.refresh()
        runCurrent()
        return FolderCleaning(folders, testEdits(services, feed)).cleanup(feed) to feed
    }

    @Test
    fun deletingNeedsSpamOrTrashThatLetsMessagesLeave() =
        runTest {
            val (cleanup) = cleanup(MailScope.Inbox)
            assertTrue(cleanup.canDelete(message("m1", "j")))
            assertFalse(cleanup.canDelete(message("m1", "t")), "Trash here keeps its messages")
            assertFalse(cleanup.canDelete(message("m1", "i")), "The inbox has no Delete forever")
            assertFalse(cleanup.canDelete(message("m1", "j").copy(emailId = "")), "Supplied mail has no server id")
            assertEquals(MailboxRole.Junk, cleanup.role(message("m1", "j")))
            assertNull(cleanup.role(message("m1", "i")))
        }

    @Test
    fun onlySpamAndTrashListsThatAllowItCanBeEmptied() =
        runTest {
            val (spam) = cleanup(MailScope.Role(MailboxRole.Junk))
            assertEquals(MailboxRole.Junk, spam.listRole())
            val emptying = assertNotNull(spam.emptying())
            assertEquals(4, emptying.count)
            assertNull(cleanup(MailScope.Folder("ada", "t")).first.emptying(), "Trash here keeps its messages")
            val (sentList) = cleanup(MailScope.Folder("ada", "s"))
            assertEquals(MailboxRole.Sent, sentList.listRole())
            assertNull(sentList.emptying())
            assertNull(cleanup(MailScope.Inbox).first.listRole())
        }

    @Test
    fun deletedMessagesLeaveAndKeptOnesComeBackWithANotice() =
        runTest {
            val (cleanup, feed) = cleanup(MailScope.Role(MailboxRole.Junk), message("m1", "j"), message("m2", "j"))
            reply = {
                """{"methodResponses": [["Email/set", {"newState": "e2", "destroyed": ["m1"],
                    "notDestroyed": {"m2": {"type": "forbidden"}}}, "c0"]]}"""
            }
            cleanup.onDelete(feed.messages)
            assertTrue(feed.messages.isEmpty(), "Both leave at once")
            settle { feed.notice != null }
            assertEquals(listOf("m2"), feed.messages.map { it.id })
            assertEquals("Couldn't finish deleting 1 message. Try again.", feed.notice)
            assertTrue(sent.value.any { "\"destroy\":[\"m1\",\"m2\"]" in it }, sent.value.toString())
        }

    @Test
    fun emptyingTakesTheLoadedMessagesOffAndDeletesTheFolder() =
        runTest {
            val (cleanup, feed) = cleanup(MailScope.Role(MailboxRole.Junk), message("m1", "j"))
            reply = { body ->
                when {
                    "Email/set" in body -> DESTROYED_M1
                    "\"position\":0" in body -> page("\"m1\"", """{"id": "m1", "mailboxIds": {"j": true}}""")
                    else -> page("", "")
                }
            }
            assertNotNull(cleanup.emptying()).run()
            assertTrue(feed.messages.isEmpty())
            settle { sent.value.any { "Email/set" in it } }
            // A refused deletion would bring the message back; give it the chance.
            settle { false }
            assertTrue(feed.messages.isEmpty())
            assertNull(feed.notice)
            assertTrue(sent.value.any { "\"destroy\":[\"m1\"]" in it }, sent.value.toString())
        }

    @Test
    fun emptyingRefreshesARowWhoseOtherMembersMovedAway() =
        runTest {
            // m2 left Spam on another device, so emptying destroys only m1 and the row comes back.
            val members = listOf(ThreadMember("m1", setOf("j"), "j"), ThreadMember("m2", setOf("i"), "i"))
            val row = message("m1", "j").copy(conversation = conversationOf(members))
            val (cleanup, feed) = cleanup(MailScope.Role(MailboxRole.Junk), row)
            reply = { body ->
                when {
                    "Email/set" in body -> DESTROYED_M1
                    "\"position\":0" in body -> page("\"m1\"", """{"id": "m1", "mailboxIds": {"j": true}}""")
                    else -> page("", "")
                }
            }
            assertNotNull(cleanup.emptying()).run()
            settle { sent.value.any { "Email/set" in it } }
            settle { false }
            assertTrue(feed.messages.isEmpty(), "The refresh drops the destroyed row")
            assertNull(feed.notice)
        }

    private fun membership(
        body: String,
        messages: List<MailMessage>,
    ): String {
        val call =
            Json
                .parseToJsonElement(body)
                .jsonObject
                .getValue("methodCalls")
                .jsonArray
                .single()
                .jsonArray
        val ids =
            call[1]
                .jsonObject
                .getValue("ids")
                .jsonArray
                .map { it.jsonPrimitive.content }
        val list =
            ids.joinToString { id ->
                val folder = messages.first { it.emailId == id }.location.mailboxId
                """{"id":"$id","mailboxIds":{"$folder":true}}"""
            }
        return """{"methodResponses":[["Email/get",{"state":"fresh","list":[$list]},"c0"]]}"""
    }

    private fun page(
        ids: String,
        list: String,
    ) = """{"methodResponses": [["Email/query", {"ids": [$ids]}, "c0"],
        ["Email/get", {"state": "e", "list": [$list]}, "c1"]]}"""

    private companion object {
        const val DESTROYED_M1 =
            """{"methodResponses": [["Email/set", {"newState": "e2", "destroyed": ["m1"]}, "c0"]]}"""
    }
}
