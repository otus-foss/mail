package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.DeviceDrafts
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.JmapEmailWriter
import com.otusfoss.mail.core.messages.data.WaitingMail
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeDrafts
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.connections
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Drafts written offline show in the Drafts list until the server has them. */
class WaitingDraftRowsTest {
    private val login = ADA_LOGIN
    private val store = InMemoryLoginStore().apply { save(login, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeMailServer(FOLDERS, email("d1", "d") + ", " + email("m1", "a"))
    private val connections = server.connections(store)
    private val queue = EmailChangeQueue(connections, cache, store = null)
    private val saves = FakeDrafts().apply { failure = OFFLINE }
    private val drafts = DeviceDrafts(saves, store = null, now = { Instant.parse("2026-10-19T08:00:00Z") })
    private val source =
        JmapMailSource(connections, cache, WaitingMail(queue, drafts), Mailboxes(cache), zone = { TimeZone.UTC })
    private val scope = MailScope.Role(MailboxRole.Drafts)
    private val bob = MailSender("Bob", "bob@example.org")
    private val written = Draft(login.id, to = listOf(bob), subject = "From the plane", body = "Hello Bob")

    @Test
    fun aWaitingDraftShowsFirstInDraftsOnlineAndFromTheCache() =
        runTest {
            val id = drafts.save(written).getOrThrow()

            val loaded = source.messages(scope).messages
            server.down = true
            val cached = source.cached(scope, MailQuery()).messages

            assertEquals(listOf(id, "d1"), loaded.map { it.emailId })
            assertEquals(loaded.map { it.emailId }, cached.map { it.emailId })
            assertEquals("From the plane" to "Hello Bob", loaded.first().let { it.subject to it.body })
            assertEquals(
                emptyList(),
                source.cached(MailScope.Inbox, MailQuery()).messages.filter { isDeviceDraftId(it.emailId) },
            )
        }

    @Test
    fun aWaitingDraftHidesTheCopyItReplaces() =
        runTest {
            source.messages(scope)
            val id = drafts.save(written.copy(emailId = "d1")).getOrThrow()

            assertEquals(listOf(id), source.cached(scope, MailQuery()).messages.map { it.emailId })
        }

    @Test
    fun aLoadStoresTheWaitingDraftsOnTheServerOnceItAnswers() =
        runTest {
            drafts.save(written).getOrThrow()
            saves.failure = null

            val loaded = source.messages(scope).messages

            assertEquals(listOf("d1"), loaded.map { it.emailId })
            assertEquals(listOf("From the plane"), saves.saved.map { it.subject })
            assertEquals(emptyList(), drafts.list.waiting(login.id))
        }

    @Test
    fun deletingAWaitingDraftsRowDropsIt() =
        runTest {
            drafts.save(written).getOrThrow()
            val row = source.messages(scope).messages.first()
            val writer = JmapEmailWriter(connections, cache, queue, drafts.list)

            assertEquals(setOf(row.id), writer.delete(listOf(row)))

            assertEquals(listOf("d1"), source.cached(scope, MailQuery()).messages.map { it.emailId })
            assertTrue(drafts.list.waiting(login.id).isEmpty())
        }

    @Test
    fun markingAWaitingDraftsRowSendsNothing() =
        runTest {
            drafts.save(written).getOrThrow()
            val row = source.messages(scope).messages.first()
            val writer = JmapEmailWriter(connections, cache, queue, drafts.list)

            assertEquals(emptySet(), writer.mark(listOf(row), MessageFlag.Flagged, on = true))

            assertTrue(server.calls("Email/set").isEmpty())
        }

    private companion object {
        val OFFLINE = DraftFailure.Mail(MailFailure.Unreachable)
        const val FOLDERS =
            """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "d", "name": "Drafts", "role": "drafts"}"""

        fun email(
            id: String,
            mailbox: String,
        ) = """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"$mailbox": true}, "keywords": {},
            "receivedAt": "2026-10-18T09:00:00Z"}"""
    }
}
