package com.otus.mail.feature.contacts.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import com.otus.mail.testing.ADA_LOGIN
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contacts kept on the device and synced by `AddressBook/changes` and `ContactCard/changes`. */
class JmapContactCacheTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val server =
        FakeContactServer().apply {
            put("AddressBook", book("b1", "Personal"))
            put("ContactCard", card("k1", "Grace"))
            put("ContactCard", card("k2", "Alan"))
        }
    private val repository = server.repository(store, cache)

    private fun ContactsSnapshot.names() = cards.associate { it.key.cardId to it.card.name?.full }

    /** The ids each `ContactCard/get` asked for by name or reference since [from] calls. */
    private fun cardFetches(from: Int) =
        server.calls
            .drop(from)
            .filter { (name, _) -> name == "ContactCard/get" }
            .mapNotNull { (_, arguments) -> arguments["ids"] as? JsonArray }
            .flatMap { ids -> ids.map { it.jsonPrimitive.content } }

    @Test
    fun theFirstSyncReadsEveryCardAndTheCacheShowsThemOffline() =
        runTest {
            val first = repository.load()
            assertEquals(mapOf("k1" to "Grace", "k2" to "Alan"), first.names())
            assertTrue("ContactCard/query" in server.names())
            assertTrue(server.names().none { it.endsWith("/changes") }, "Nothing is saved to follow yet")

            server.down = true
            val offline = server.repository(store, cache).load()
            assertEquals(first.names(), offline.names())
            assertEquals(listOf("Personal"), offline.books.map { it.name })
            assertEquals(listOf(ADA_LOGIN.id), offline.failedLogins)
        }

    @Test
    fun laterSyncsFetchOnlyWhatChanged() =
        runTest {
            repository.load()
            server.put("ContactCard", card("k1", "Grace Hopper"))
            server.destroy("ContactCard", "k2")
            server.put("ContactCard", card("k3", "Ada", book = "b2"))
            server.put("AddressBook", book("b2", "Work"))
            val from = server.calls.size

            val second = repository.load()

            assertEquals(mapOf("k1" to "Grace Hopper", "k3" to "Ada"), second.names())
            assertEquals(listOf("Personal", "Work"), second.books.map { it.name })
            assertEquals(setOf("k1", "k3"), cardFetches(from).toSet(), "Only the changed cards are fetched")
            val names = server.names(from)
            assertTrue("ContactCard/query" !in names, "No card is read page by page again")
            assertTrue("ContactCard/changes" in names && "AddressBook/changes" in names)
        }

    @Test
    fun cannotCalculateChangesReadsEverythingAgain() =
        runTest {
            repository.load()
            server.forgotStates = true
            server.destroy("ContactCard", "k1")
            server.put("ContactCard", card("k3", "Ada"))
            val from = server.calls.size

            val second = repository.load()

            assertEquals(mapOf("k2" to "Alan", "k3" to "Ada"), second.names())
            assertTrue("ContactCard/query" in server.names(from))
            server.down = true
            val offline = server.repository(store, cache).load()
            assertEquals(second.names(), offline.names(), "The cache drops the card the server no longer has")
        }
}
