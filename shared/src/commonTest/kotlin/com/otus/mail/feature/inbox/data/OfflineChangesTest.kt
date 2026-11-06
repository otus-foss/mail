package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.data.merged
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Marks and moves made offline wait on the device, show at once, and reach the server later. */
class OfflineChangesTest {
    private class Store :
        MemoryMapStore(),
        EmailChangeStore

    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val changes = Store()
    private val server = FakeMailServer(INBOX, email("m1", 10))
    private val queue = EmailChangeQueue(server.connections(store), cache, changes)
    private val source = server.source(store, cache, queue = queue)
    private val writer = emailWriter(server.connections(store), cache, queue)

    private suspend fun message() = source.messages(MailScope.Inbox, MailQuery()).messages.single()

    @Test
    fun anOfflineMarkShowsAtOnceAndReachesTheServerWithTheNextSync() =
        runTest {
            val message = message()
            server.down = true
            assertEquals(
                setOf(message.id),
                writer.mark(listOf(message), MessageFlag.Read, on = true),
                "It counts as made",
            )
            assertFalse(
                source
                    .cached(MailScope.Inbox, MailQuery())
                    .messages
                    .single()
                    .attributes.unread,
                "It shows before the server has it",
            )
            assertTrue(changes.saved.isNotEmpty(), "It waits on the device")

            server.down = false
            val restarted =
                server.source(store, cache, queue = EmailChangeQueue(server.connections(store), cache, changes))
            assertFalse(
                restarted
                    .messages(MailScope.Inbox, MailQuery())
                    .messages
                    .single()
                    .attributes.unread,
            )
            assertEquals(JsonPrimitive(true), server.calls("Email/set").single().patchOf("m1")["keywords/\$seen"])
            assertTrue(changes.saved.isEmpty())
        }

    @Test
    fun laterChangesOfAnEmailGoAsOnePatch() =
        runTest {
            val message = message()
            server.down = true
            writer.mark(listOf(message), MessageFlag.Read, on = true)
            writer.mark(listOf(message), MessageFlag.Read, on = false)
            server.down = false
            source.messages(MailScope.Inbox, MailQuery())
            assertEquals(JsonNull, server.calls("Email/set").single().patchOf("m1")["keywords/\$seen"])
        }

    @Test
    fun aChangeStillWaitingShowsAfterASyncThatCouldNotSendIt() =
        runTest {
            val message = message()
            server.down = true
            writer.mark(listOf(message), MessageFlag.Flagged, on = true)
            assertTrue(
                source
                    .messages(MailScope.Inbox, MailQuery())
                    .messages
                    .single()
                    .attributes.flagged,
            )
        }

    @Test
    fun aRefusedChangeIsDroppedAndTheEmailReadAgain() =
        runTest {
            val message = message()
            server.refusedUpdates = setOf("m1")
            assertTrue(writer.mark(listOf(message), MessageFlag.Read, on = true).isEmpty())
            assertTrue(changes.saved.isEmpty())
            assertTrue(
                source
                    .messages(MailScope.Inbox, MailQuery())
                    .messages
                    .single()
                    .attributes.unread,
            )
        }

    @Test
    fun aServerDownForNowKeepsTheChangeForTheNextSync() =
        runTest {
            val message = message()
            server.refused = setOf("Email/set")
            server.refusalType = "serverUnavailable"
            writer.mark(listOf(message), MessageFlag.Read, on = true)
            assertTrue(changes.saved.isNotEmpty(), "It waits")

            server.refused = emptySet()
            source.messages(MailScope.Inbox, MailQuery())
            assertEquals(2, server.calls("Email/set").size)
            assertTrue(changes.saved.isEmpty())
        }

    @Test
    fun aChangeTheServerTakesOnlyLaterWaitsForTheNextSync() =
        runTest {
            val message = message()
            server.refusedUpdates = setOf("m1")
            server.updateRefusal = "rateLimit"
            assertEquals(setOf(message.id), writer.mark(listOf(message), MessageFlag.Read, on = true))
            assertTrue(changes.saved.isNotEmpty(), "It waits")

            server.refusedUpdates = emptySet()
            source.messages(MailScope.Inbox, MailQuery())
            assertEquals(2, server.calls("Email/set").size)
            assertTrue(changes.saved.isEmpty())
        }

    @Test
    fun changesOfTwoLoginsMadeAtOnceBothWait() =
        runTest {
            store.save(GRACE_LOGIN, "secret")
            val io = StandardTestDispatcher(testScheduler)
            val both = EmailChangeQueue(server.connections(store), cache, changes, io = io)
            server.down = true
            val patch = mapOf("m1" to JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true))))
            listOf(ADA_LOGIN.id, GRACE_LOGIN.id)
                .map { login -> async { both.update(login, patch) } }
                .awaitAll()
            assertEquals(setOf(ADA_LOGIN.id, GRACE_LOGIN.id), changes.saved.keys, "Neither write lost the other")
        }

    @Test
    fun anOfflineNotSpamWaitsLikeAMove() =
        runTest {
            val inbox = message()
            val spam = inbox.copy(location = inbox.location.copy(mailboxId = "r", mailboxIds = setOf("r")))
            server.down = true
            assertTrue(writer.notSpam(spam, inboxId = "a", keywords = false), "It counts as made")
            assertTrue(changes.saved.isNotEmpty(), "It waits on the device")

            server.down = false
            assertFalse(queue.flushAll())
            assertEquals(JsonPrimitive(true), server.calls("Email/set").single().patchOf("m1")["mailboxIds/a"])
        }

    @Test
    fun aDeletedEmailLeavesTheDevicesCopy() =
        runTest {
            val message = message()
            assertEquals(setOf(message.id), writer.delete(listOf(message)))
            assertTrue(source.cached(MailScope.Inbox, MailQuery()).messages.isEmpty(), "The list no longer shows it")
        }

    @Test
    fun aWholePropertyAndAKeyBelowItMergeIntoOneValidPatch() {
        val whole = mapOf("m1" to JsonObject(mapOf("mailboxIds" to JsonObject(mapOf("a" to JsonPrimitive(true))))))
        val key = mapOf("m1" to JsonObject(mapOf("mailboxIds/r" to JsonPrimitive(true))))
        val expected =
            JsonObject(
                mapOf("mailboxIds" to JsonObject(mapOf("a" to JsonPrimitive(true), "r" to JsonPrimitive(true)))),
            )
        assertEquals(expected, whole.merged(key).getValue("m1"))
        assertEquals(
            whole.getValue("m1"),
            key.merged(whole).getValue("m1"),
            "A whole property replaces the keys below it",
        )
    }

    private fun JsonObject.patchOf(id: String) = (getValue("update") as JsonObject).getValue(id) as JsonObject

    private companion object {
        val GRACE_LOGIN = JmapLogin("grace", ADA_LOGIN.sessionUrl, "grace@example.org", "Grace")

        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}"""

        fun email(
            id: String,
            hour: Int,
        ) = """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T${hour.toString().padStart(2, '0')}:00:00Z"}"""
    }
}
