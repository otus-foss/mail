package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mail.mailboxes
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapFolderCacheTest {
    private val login = ADA_LOGIN
    private val cache = InMemoryMailCache()
    private var calls = 0

    private fun repository(): JmapFolderRepository {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val api = { _: String ->
            calls++
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
               {"id": "a", "name": "Inbox", "role": "inbox", "unreadEmails": 3, "totalEmails": 9},
               {"id": "r", "name": "Receipts", "parentId": "a"}]}, "c0"]]}"""
        }
        return JmapFolderRepository(JmapConnections(JmapClient(stalwartLike(api = api)), store), store, cache)
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
            assertEquals(setOf("a", "r"), cache.mailboxes("ada").map { it.id }.toSet())
            val callsBefore = calls
            val cached = repository().cachedAccounts().single()
            assertEquals(callsBefore, calls, "The cached load asks no server")
            assertEquals(3, cached.inbox?.unread)
            assertEquals("a", cached.folder("r")?.parentId)
        }
}
