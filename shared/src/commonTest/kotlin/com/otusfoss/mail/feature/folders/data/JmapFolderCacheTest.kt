package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapFolderCacheTest {
    private val login = ADA_LOGIN
    private val cache = InMemoryMailCache()
    private var calls = 0
    private val bodies = mutableListOf<String>()

    private fun repository(): JmapFolderRepository {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val api = { body: String ->
            calls++
            bodies += body
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
               {"id": "a", "name": "Inbox", "role": "inbox", "unreadEmails": 3, "totalEmails": 9},
               {"id": "r", "name": "Receipts", "parentId": "a"}]}, "c0"]]}"""
        }
        return JmapFolderRepository(JmapConnections(JmapClient(stalwartLike(api = api)), store), Mailboxes(cache))
    }

    @Test
    fun anAccountNeverLoadedHasNoCachedFolders() =
        runTest {
            val cached = repository().cachedAccounts().single()
            assertEquals("ada", cached.accountId)
            assertTrue(cached.folders.isEmpty())
            assertEquals(0, calls)
        }

    @Test
    fun loadedMailboxesComeBackFromTheDeviceWithTheirCounts() =
        runTest {
            repository().accounts()
            assertEquals(setOf("a", "r"), Mailboxes(cache).cached("ada").map { it.id }.toSet())
            val callsBefore = calls
            val cached = repository().cachedAccounts().single()
            assertEquals(callsBefore, calls, "The cached load asks no server")
            assertEquals(3, cached.inbox?.unread)
            assertEquals("a", cached.folder("r")?.parentId)
        }

    @Test
    fun aLoadAfterTheFirstAsksForTheChangesSinceTheSavedState() =
        runTest {
            repository().accounts()
            repository().accounts()
            val again = bodies.first { "Mailbox/changes" in it }
            assertTrue("\"sinceState\":\"m\"" in again, "The folders saved the state they matched")
        }
}
