package com.otusfoss.mail.core.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** What every [MailCache] promises. Each implementation's test runs these against a new cache. */
abstract class MailCacheContractTest {
    abstract fun newCache(): MailCache

    @Test
    fun keepsEachLoginAndKindApart() =
        runTest {
            val cache = newCache()
            cache.write("ada") {
                put(CacheKind.Email, "e1", "ada email")
                put(CacheKind.Thread, "e1", "ada thread")
            }
            cache.write("bob") { put(CacheKind.Email, "e1", "bob email") }

            assertEquals(mapOf("e1" to "ada email"), cache.read("ada", CacheKind.Email, listOf("e1", "missing")))
            assertEquals(mapOf("e1" to "ada thread"), cache.readAll("ada", CacheKind.Thread))
            assertEquals(mapOf("e1" to "bob email"), cache.readAll("bob", CacheKind.Email))
            assertEquals(emptyMap(), cache.readAll("bob", CacheKind.Thread))
            assertEquals(emptyMap(), cache.read("carol", CacheKind.Email, listOf("e1")))
        }

    @Test
    fun putReplacesTheObjectSavedBefore() =
        runTest {
            val cache = newCache()
            cache.write("ada") { put(CacheKind.Mailbox, "inbox", "old") }
            cache.write("ada") { put(CacheKind.Mailbox, "inbox", "new") }

            assertEquals(mapOf("inbox" to "new"), cache.readAll("ada", CacheKind.Mailbox))
        }

    @Test
    fun aWriteThatFailsSavesNothing() =
        runTest {
            val cache = newCache()
            cache.write("ada") {
                put(CacheKind.Email, "kept", "old")
                putValue("Email", "s1")
            }

            assertFailsWith<IllegalStateException> {
                cache.write("ada") {
                    put(CacheKind.Email, "kept", "new")
                    put(CacheKind.Email, "added", "new")
                    putValue("Email", "s2")
                    error("interrupted")
                }
            }

            assertEquals(mapOf("kept" to "old"), cache.readAll("ada", CacheKind.Email))
            assertEquals("s1", cache.value("ada", "Email"))
        }

    @Test
    fun removeAndClearDropOnlyTheirObjects() =
        runTest {
            val cache = newCache()
            cache.write("ada") {
                listOf("e1", "e2", "e3").forEach { put(CacheKind.Email, it, it) }
                put(CacheKind.Thread, "t1", "t1")
            }

            cache.write("ada") { remove(CacheKind.Email, listOf("e1", "e3", "missing")) }
            assertEquals(mapOf("e2" to "e2"), cache.readAll("ada", CacheKind.Email))

            cache.write("ada") { clear(CacheKind.Email) }
            assertEquals(emptyMap(), cache.readAll("ada", CacheKind.Email))
            assertEquals(mapOf("t1" to "t1"), cache.readAll("ada", CacheKind.Thread))
        }

    @Test
    fun valuesAreSavedReplacedAndRemoved() =
        runTest {
            val cache = newCache()
            cache.write("ada") {
                putValue("Email", "s1")
                putValue("Thread", "t1")
            }
            cache.write("ada") { putValue("Email", "s2") }
            cache.write("bob") { putValue("Email", "b1") }

            assertEquals("s2", cache.value("ada", "Email"))
            assertEquals("b1", cache.value("bob", "Email"))

            cache.write("ada") { putValue("Thread", null) }
            assertNull(cache.value("ada", "Thread"))
            assertNull(cache.value("ada", "Mailbox"))
        }

    @Test
    fun trimDropsOlderObjectsOfTheKindForEveryLogin() =
        runTest {
            val cache = newCache()
            listOf("ada", "bob").forEach { login ->
                cache.write(login) {
                    put(CacheKind.Body, "old", "old", receivedMillis = 1_000)
                    put(CacheKind.Body, "edge", "edge", receivedMillis = 2_000)
                    put(CacheKind.Body, "new", "new", receivedMillis = 3_000)
                    put(CacheKind.Body, "undated", "undated")
                    put(CacheKind.Email, "old", "old", receivedMillis = 1_000)
                }
            }

            cache.trim(CacheKind.Body, epochMillis = 2_000)

            listOf("ada", "bob").forEach { login ->
                assertEquals(setOf("edge", "new", "undated"), cache.readAll(login, CacheKind.Body).keys)
                assertEquals(setOf("old"), cache.readAll(login, CacheKind.Email).keys)
            }
        }

    @Test
    fun forgetRemovesOnlyThatLogin() =
        runTest {
            val cache = newCache()
            listOf("ada", "bob").forEach { login ->
                cache.write(login) {
                    put(CacheKind.Email, "e1", login)
                    put(CacheKind.Body, "e1", login, receivedMillis = 1_000)
                    putValue("Email", login)
                }
            }

            cache.forget("ada")

            assertEquals(emptyMap(), cache.readAll("ada", CacheKind.Email))
            assertEquals(emptyMap(), cache.readAll("ada", CacheKind.Body))
            assertNull(cache.value("ada", "Email"))
            assertEquals(mapOf("e1" to "bob"), cache.readAll("bob", CacheKind.Email))
            assertEquals("bob", cache.value("bob", "Email"))
        }

    @Test
    fun readsAndRemovesManyIdsAtOnce() =
        runTest {
            val cache = newCache()
            val ids = (1..2_400).map { "e$it" }
            cache.write("ada") { ids.forEach { put(CacheKind.Member, it, "json $it") } }

            val read = cache.read("ada", CacheKind.Member, ids + "missing")
            assertEquals(ids.associateWith { "json $it" }, read)

            cache.write("ada") { remove(CacheKind.Member, ids.drop(1)) }
            assertEquals(mapOf("e1" to "json e1"), cache.readAll("ada", CacheKind.Member))
        }
}
