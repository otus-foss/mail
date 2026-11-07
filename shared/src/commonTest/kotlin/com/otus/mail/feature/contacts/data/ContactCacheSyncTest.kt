package com.otus.mail.feature.contacts.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.testing.ADA_LOGIN
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contacts load from the device's copy and follow the server's changes instead of reading every card. */
class ContactCacheSyncTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeContactServer().apply { put("c1", "Ada") }
    private val repository = JmapContactRepository(server.connections(store), cache)

    private suspend fun names() = repository.load().cards.map { it.displayName }

    @Test
    fun aSecondLoadAsksForChangesInsteadOfEveryCard() =
        runTest {
            assertEquals(listOf("Ada"), names())
            server.put("c1", "Ada Lovelace")
            server.put("c2", "Grace")
            val calls = server.calls.size
            assertEquals(listOf("Ada Lovelace", "Grace"), names().sorted())
            val later = server.calls.drop(calls)
            assertTrue("ContactCard/changes" in later)
            assertTrue("AddressBook/changes" in later)
            assertTrue("ContactCard/query" !in later, "The cards are not read again")
        }

    @Test
    fun aDestroyedCardLeavesTheCache() =
        runTest {
            server.put("c2", "Grace")
            names()
            server.destroy("c1")
            assertEquals(listOf("Grace"), names())
            assertEquals(listOf("Grace"), repository.cached()?.cards?.map { it.displayName })
        }

    @Test
    fun aServerThatCannotCalculateChangesIsReadAgain() =
        runTest {
            names()
            server.forgotStates = true
            server.put("c1", "Ada Lovelace")
            val calls = server.calls.size
            assertEquals(listOf("Ada Lovelace"), names())
            assertTrue("ContactCard/query" in server.calls.drop(calls))
        }

    @Test
    fun offlineTheCachedCardsShow() =
        runTest {
            assertNull(repository.cached())
            names()
            server.down = true
            val loaded = repository.load()
            assertEquals(listOf("Ada"), loaded.cards.map { it.displayName })
            assertEquals(listOf("Personal"), loaded.books.map { it.name })
            assertEquals(listOf(ADA_LOGIN.id), loaded.failedLogins)
            assertEquals(listOf("Ada"), repository.cached()?.cards?.map { it.displayName })
        }
}
