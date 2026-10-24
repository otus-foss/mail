package com.otus.mail.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountMailCacheTest {
    @Test
    fun removalCancelsDownloadsAndClearsExistingData() =
        runTest {
            val storage = InMemoryMailCache()
            val cache = AccountMailCache(storage)
            storage.write("a") { put(CacheKind.Body, "old", "body") }
            val pending = CompletableDeferred<Unit>()
            var published = false
            val request =
                launch {
                    cache.withAccount("a") { scoped ->
                        pending.await()
                        scoped.write("a") { put(CacheKind.Body, "late", "body") }
                        published = true
                    }
                }
            runCurrent()

            cache.forget("a")
            pending.complete(Unit)
            request.join()

            assertTrue(request.isCancelled)
            assertFalse(published)
            assertTrue(storage.readAll("a", CacheKind.Body).isEmpty())
        }

    @Test
    fun reconnectRejectsOldWritesEvenWhenTheirCoroutineIgnoresCancellation() =
        runTest {
            val storage = InMemoryMailCache()
            val cache = AccountMailCache(storage)
            val release = CompletableDeferred<Unit>()
            var rejected = false
            val request =
                launch {
                    cache.withAccount("a") { scoped ->
                        withContext(NonCancellable) {
                            release.await()
                            assertFailsWith<CancellationException> {
                                scoped.write("a") { put(CacheKind.Email, "old", "old credentials") }
                            }
                            rejected = true
                        }
                    }
                }
            runCurrent()

            cache.forget("a")
            cache.connected("a")
            cache.withAccount("a") { scoped ->
                scoped.write("a") { put(CacheKind.Email, "new", "new credentials") }
            }
            release.complete(Unit)
            request.join()

            assertTrue(rejected)
            assertEquals(mapOf("new" to "new credentials"), storage.readAll("a", CacheKind.Email))
        }

    @Test
    fun reconnectKeepsCachedMailWithoutTouchingStorage() =
        runTest {
            val disk = InMemoryMailCache()
            val cache =
                AccountMailCache(
                    object : MailCache by disk {
                        override suspend fun forget(loginId: String) = error("Disk full")
                    },
                )
            disk.write("a") { put(CacheKind.Email, "kept", "mail") }

            cache.connected("a")
            cache.withAccount("a") { it.write("a") { putValue("state", "a1") } }

            assertEquals(mapOf("kept" to "mail"), disk.readAll("a", CacheKind.Email))
            assertEquals("a1", cache.value("a", "state"))
        }

    @Test
    fun removalBlocksNewWorkUntilReconnectAndLeavesOtherAccountsAlone() =
        runTest {
            val cache = AccountMailCache(InMemoryMailCache())
            cache.forget("a")
            assertFailsWith<CancellationException> { cache.withAccount("a") { error("must not start") } }
            cache.withAccount("b") { it.write("b") { putValue("state", "b1") } }

            cache.connected("a")
            cache.withAccount("a") { it.write("a") { putValue("state", "a1") } }

            assertEquals("a1", cache.value("a", "state"))
            assertEquals("b1", cache.value("b", "state"))
        }
}
