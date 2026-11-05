package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JmapEmailShortcutsTest {
    private val sent = mutableListOf<String>()

    private fun shortcuts(mailboxes: String = MAILBOXES): JmapEmailShortcuts {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val server =
            stalwartLike(STALWART_SESSION) { body ->
                sent += body
                if ("Mailbox/get" in body) """{"methodResponses": [["Mailbox/get", $mailboxes, "c0"]]}""" else UPDATED
            }
        return JmapEmailShortcuts(JmapConnections(JmapClient(server), store), store)
    }

    @Test
    fun markReadAddsSeenToTheEmail() =
        runTest {
            assertTrue(shortcuts().markRead("ada", "m1"))
            assertTrue("\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in sent.single(), sent.single())
        }

    @Test
    fun archiveMovesTheEmailFromTheInboxToTheArchive() =
        runTest {
            assertTrue(shortcuts().archive("ada", "m1"))
            assertEquals(2, sent.size)
            assertTrue("\"update\":{\"m1\":{\"mailboxIds/i\":null,\"mailboxIds/a\":true}}" in sent[1], sent[1])
        }

    @Test
    fun flagAddsFlaggedToTheEmail() =
        runTest {
            assertTrue(shortcuts().flag("ada", "m1"))
            assertTrue("\"update\":{\"m1\":{\"keywords/\$flagged\":true}}" in sent.single(), sent.single())
        }

    @Test
    fun trashAndSpamPutTheEmailInTheirMailboxAlone() =
        runTest {
            assertTrue(shortcuts().trash("ada", "m1"))
            assertTrue("\"update\":{\"m1\":{\"mailboxIds\":{\"t\":true}}}" in sent[1], sent[1])
            assertTrue(shortcuts().reportSpam("ada", "m1"))
            assertTrue("\"update\":{\"m1\":{\"mailboxIds\":{\"j\":true}}}" in sent[3], sent[3])
        }

    @Test
    fun anAccountWithoutTrashOrSpamKeepsTheEmail() =
        runTest {
            val inboxOnly = """{"state": "m", "list": [{"id": "i", "role": "inbox"}]}"""
            assertFalse(shortcuts(inboxOnly).trash("ada", "m1"))
            assertFalse(shortcuts(inboxOnly).reportSpam("ada", "m1"))
            assertEquals(2, sent.size)
        }

    @Test
    fun anAccountWithoutArchiveKeepsTheEmail() =
        runTest {
            val noArchive = """{"state": "m", "list": [{"id": "i", "role": "inbox"}]}"""
            assertFalse(shortcuts(noArchive).archive("ada", "m1"))
            assertEquals(1, sent.size)
        }

    @Test
    fun aRemovedLoginOrARefusalFails() =
        runTest {
            assertFalse(shortcuts().markRead("bob", "m1"))
            assertFalse(shortcuts().archive("bob", "m1"))
            assertFalse(shortcuts().markRead("ada", "m2"))
        }

    private companion object {
        const val MAILBOXES =
            """{"state": "m", "list": [{"id": "i", "role": "inbox"}, {"id": "a", "role": "archive"}, """ +
                """{"id": "t", "role": "trash"}, {"id": "j", "role": "Junk"}, {"id": "p"}]}"""
        const val UPDATED =
            """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"m1": null}}, "c0"]]}"""
    }
}
