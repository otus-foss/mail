package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.feature.inbox.domain.EmailChangeStore
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.MemoryMapStore
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
    private val queue = EmailChangeQueue(changes)
    private val source = server.source(store, cache, queue = queue)
    private val marker = JmapMessageMarker(server.connections(store), store, cache, queue)

    private suspend fun message() = source.messages(MailScope.Inbox, MailQuery()).messages.single()

    @Test
    fun anOfflineMarkShowsAtOnceAndReachesTheServerWithTheNextSync() =
        runTest {
            val message = message()
            server.down = true
            assertEquals(
                setOf(message.id),
                marker.mark(listOf(message), MessageFlag.Read, on = true),
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
            val restarted = server.source(store, cache, queue = EmailChangeQueue(changes))
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
            marker.mark(listOf(message), MessageFlag.Read, on = true)
            marker.mark(listOf(message), MessageFlag.Read, on = false)
            server.down = false
            source.messages(MailScope.Inbox, MailQuery())
            assertEquals(JsonNull, server.calls("Email/set").single().patchOf("m1")["keywords/\$seen"])
        }

    @Test
    fun aChangeStillWaitingShowsAfterASyncThatCouldNotSendIt() =
        runTest {
            val message = message()
            server.down = true
            marker.mark(listOf(message), MessageFlag.Flagged, on = true)
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
            assertTrue(marker.mark(listOf(message), MessageFlag.Read, on = true).isEmpty())
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
        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}"""

        fun email(
            id: String,
            hour: Int,
        ) = """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T${hour.toString().padStart(2, '0')}:00:00Z"}"""
    }
}
