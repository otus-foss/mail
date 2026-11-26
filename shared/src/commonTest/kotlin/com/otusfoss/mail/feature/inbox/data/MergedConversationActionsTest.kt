package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.domain.EmailWriter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.leavingFolders
import com.otusfoss.mail.feature.inbox.domain.MailSource
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.connections
import com.otusfoss.mail.testing.emailWriter
import com.otusfoss.mail.testing.source
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Actions on a conversation row merged from several folders of one account. */
class MergedConversationActionsTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }

    private class Mail(
        val server: FakeMailServer,
        val source: MailSource,
        val writer: EmailWriter,
    )

    private fun mail(emails: String): Mail {
        val server = FakeMailServer(MAILBOXES, emails)
        val cache = InMemoryMailCache()
        val queue = EmailChangeQueue(server.connections(store), cache, store = null)
        return Mail(
            server,
            server.source(store, cache, queue = queue),
            emailWriter(server.connections(store), cache, queue),
        )
    }

    /** The mailboxes the server holds [id] in now; null once it is destroyed. */
    private fun FakeMailServer.boxesOf(id: String): Set<String>? = emailField(id, "mailboxIds")

    /** The keywords the server holds on [id] now; null once it is destroyed. */
    private fun FakeMailServer.keywordsOf(id: String): Set<String>? = emailField(id, "keywords")

    private fun FakeMailServer.emailField(
        id: String,
        field: String,
    ): Set<String>? {
        val body = """{"methodCalls": [["Email/get", {"ids": ["$id"], "properties": ["id", "$field"]}, "c0"]]}"""
        return Json
            .parseToJsonElement(answer(body))
            .jsonObject
            .getValue("methodResponses")
            .jsonArray[0]
            .jsonArray[1]
            .jsonObject
            .getValue("list")
            .jsonArray
            .firstOrNull()
            ?.jsonObject
            ?.getValue(field)
            ?.jsonObject
            ?.keys
    }

    private fun leaving(
        row: MailMessage,
        shortcut: MailboxRole?,
    ): Set<String> = row.leavingFolders(shortcut, inboxId = INBOX)

    @Test
    fun deletingARowForGoodKeepsItsEmailsThatAreNotInTrash() =
        runTest {
            val mail = mail("${received("r1", 1, INBOX)}, ${received("r2", 3, INBOX)}, ${received("x1", 5, TRASH)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Trash))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(MailboxRole.Trash, row.location.mailboxRole)
            assertEquals(listOf("x1", "r2", "r1"), row.conversation!!.members.map { it.emailId })

            mail.writer.delete(listOf(row))

            assertNull(mail.server.boxesOf("x1"), "The trashed email is destroyed")
            assertEquals(setOf(INBOX), mail.server.boxesOf("r2"), "An email only in the Inbox stays")
            assertEquals(setOf(INBOX), mail.server.boxesOf("r1"), "An email only in the Inbox stays")
        }

    @Test
    fun deletingARowForGoodKeepsItsEmailsInCustomFolders() =
        runTest {
            val mail = mail("${received("r1", 1, RECEIPTS)}, ${received("x1", 5, TRASH)}")
            val row =
                mail.source
                    .messages(MailScope.AllMailboxes)
                    .messages
                    .single()
            assertEquals(MailboxRole.Trash, row.location.mailboxRole)

            mail.writer.delete(listOf(row))

            assertNull(mail.server.boxesOf("x1"), "The trashed email is destroyed")
            assertEquals(setOf(RECEIPTS), mail.server.boxesOf("r1"), "An email in a custom folder stays")
        }

    @Test
    fun emptyingRefusesFoldersThatAreNotTrashOrSpam() =
        runTest {
            val mail = mail(received("r1", 1, INBOX))
            mail.source.messages(MailScope.Inbox)

            val deletion = mail.writer.empty(ADA_LOGIN.id, INBOX)

            assertFalse(deletion.complete)
            assertEquals(emptySet(), deletion.emailIds)
            assertEquals(setOf(INBOX), mail.server.boxesOf("r1"))
        }

    @Test
    fun movingAConversationTakesItsReceivedEmailsOutOfTheInboxAndUndoRestoresThem() =
        runTest {
            val mail = mail("${received("r1", 1, INBOX)}, ${received("r2", 3, INBOX)}, ${reply("s1", 4)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Sent))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(SENT, row.location.mailboxId)
            val left = leaving(row, shortcut = null)

            assertTrue(mail.writer.move(row, RECEIPTS, left))

            assertEquals(setOf(RECEIPTS), mail.server.boxesOf("r2"), "The received email moves to Receipts")
            assertEquals(setOf(RECEIPTS), mail.server.boxesOf("r1"), "The received email moves to Receipts")
            assertEquals(setOf(SENT), mail.server.boxesOf("s1"), "The reply stays in Sent")

            assertTrue(mail.writer.moveBack(row, RECEIPTS, left))

            assertEquals(setOf(INBOX), mail.server.boxesOf("r2"))
            assertEquals(setOf(INBOX), mail.server.boxesOf("r1"))
            assertEquals(setOf(SENT), mail.server.boxesOf("s1"))
        }

    @Test
    fun archivingAConversationLeavesTheUsersReplyInSentAndUndoRestoresTheInbox() =
        runTest {
            val mail = mail("${received("r1", 1, INBOX)}, ${reply("s1", 4)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Sent))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(SENT, row.location.mailboxId)
            val left = leaving(row, MailboxRole.Archive)

            assertTrue(mail.writer.move(row, ARCHIVE, left))

            assertEquals(setOf(ARCHIVE), mail.server.boxesOf("r1"), "The received email is archived")
            assertEquals(setOf(SENT), mail.server.boxesOf("s1"), "The reply stays in Sent")

            assertTrue(mail.writer.moveBack(row, ARCHIVE, left))

            assertEquals(setOf(INBOX), mail.server.boxesOf("r1"))
            assertEquals(setOf(SENT), mail.server.boxesOf("s1"))
        }

    @Test
    fun archivingAConversationDoesNotFileItsTrashedEmailInArchive() =
        runTest {
            val mail = mail("${received("x0", 1, TRASH)}, ${received("r3", 6, INBOX)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Trash))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(INBOX, row.location.mailboxId)
            assertEquals(listOf("r3", "x0"), row.conversation!!.members.map { it.emailId })
            val left = leaving(row, MailboxRole.Archive)

            assertTrue(mail.writer.move(row, ARCHIVE, left))

            assertEquals(setOf(ARCHIVE), mail.server.boxesOf("r3"), "The Inbox email is archived")
            assertEquals(setOf(TRASH), mail.server.boxesOf("x0"), "The deleted email stays in Trash alone")

            assertTrue(mail.writer.moveBack(row, ARCHIVE, left))

            assertEquals(setOf(INBOX), mail.server.boxesOf("r3"))
            assertEquals(setOf(TRASH), mail.server.boxesOf("x0"))
        }

    @Test
    fun restoringATrashedRowMovesOnlyItsTrashedEmailsToTheInbox() =
        runTest {
            val mail = mail("${received("a1", 1, ARCHIVE)}, ${received("x1", 5, TRASH)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Archive, MailboxRole.Trash))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(TRASH, row.location.mailboxId)
            val left = leaving(row, MailboxRole.Inbox)

            assertTrue(mail.writer.move(row, INBOX, left))

            assertEquals(setOf(INBOX), mail.server.boxesOf("x1"), "The trashed email is restored to Inbox")
            assertEquals(setOf(ARCHIVE), mail.server.boxesOf("a1"), "The archived email stays in Archive")

            assertTrue(mail.writer.moveBack(row, INBOX, left))

            assertEquals(setOf(TRASH), mail.server.boxesOf("x1"))
            assertEquals(setOf(ARCHIVE), mail.server.boxesOf("a1"))
        }

    @Test
    fun notSpamMovesOnlyEmailsInSpamAndLeavesOtherFoldersAlone() =
        runTest {
            val mail = mail("${received("a1", 1, ARCHIVE)}, ${received("j1", 5, JUNK)}")
            val scope = MailScope.Roles(listOf(MailboxRole.Archive, MailboxRole.Junk))
            val row =
                mail.source
                    .messages(scope)
                    .messages
                    .single()
            assertEquals(JUNK, row.location.mailboxId)

            assertTrue(mail.writer.notSpam(row, INBOX, keywords = true))

            assertEquals(setOf(INBOX), mail.server.boxesOf("j1"), "The spam email moves to Inbox")
            assertTrue("\$notjunk" in mail.server.keywordsOf("j1").orEmpty())
            assertEquals(setOf(ARCHIVE), mail.server.boxesOf("a1"), "The archived email stays in Archive")
            assertFalse("\$notjunk" in mail.server.keywordsOf("a1").orEmpty())
        }

    private companion object {
        const val INBOX = "a"
        const val SENT = "s"
        const val TRASH = "t"
        const val JUNK = "j"
        const val ARCHIVE = "v"
        const val RECEIPTS = "r"

        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"},
            {"id": "s", "name": "Sent", "role": "sent"}, {"id": "t", "name": "Trash", "role": "trash"},
            {"id": "j", "name": "Spam", "role": "junk"}, {"id": "v", "name": "Archive", "role": "archive"},
            {"id": "r", "name": "Receipts"}"""

        const val GRACE = """[{"name": "Grace", "email": "grace@example.org"}]"""
        const val ADA = """[{"name": "Ada", "email": "ada@example.org"}]"""

        fun received(
            id: String,
            day: Int,
            box: String,
        ) = """{"id": "$id", "threadId": "t1", "mailboxIds": {"$box": true}, "keywords": {}, "subject": "Plans",
            "from": $GRACE, "to": $ADA, "receivedAt": "2026-10-0${day}T09:00:00Z"}"""

        fun reply(
            id: String,
            day: Int,
        ) = """{"id": "$id", "threadId": "t1", "mailboxIds": {"s": true}, "keywords": {"${'$'}seen": true},
            "subject": "Re: Plans", "from": $ADA, "to": $GRACE, "receivedAt": "2026-10-0${day}T09:00:00Z"}"""
    }
}
