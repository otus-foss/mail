package com.otus.mail.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecoveringMailCacheTest {
    @Test
    fun failedWritesContinueInMemoryWithoutRetryingTheDisk() =
        runTest {
            var writes = 0
            val disk =
                object : MailCache by InMemoryMailCache() {
                    override suspend fun write(
                        loginId: String,
                        changes: CacheWrite.() -> Unit,
                    ) {
                        writes += 1
                        throw CacheStorageException(IllegalStateException("disk full"))
                    }
                }
            val cache = RecoveringMailCache(disk)
            assertFailsWith<CacheStorageException> { cache.write("a") { put(CacheKind.Body, "e", "body") } }
            cache.write("a") { putValue("state", "s1") }
            assertEquals(mapOf("e" to "body"), cache.readAll("a", CacheKind.Body))
            assertEquals("s1", cache.value("a", "state"))
            assertEquals(1, writes)
        }

    @Test
    fun failedReadsBecomeCacheMissesAndCanBeRepopulated() =
        runTest {
            val disk =
                object : MailCache by InMemoryMailCache() {
                    override suspend fun readAll(
                        loginId: String,
                        kind: CacheKind,
                    ): Map<String, String> = throw CacheStorageException(IllegalStateException("corrupt"))
                }
            val cache = RecoveringMailCache(disk)
            assertTrue(cache.readAll("a", CacheKind.Email).isEmpty())
            cache.write("a") { put(CacheKind.Email, "e", "fresh") }
            assertEquals(mapOf("e" to "fresh"), cache.readAll("a", CacheKind.Email))
        }

    @Test
    fun aTransientLockFailsOneOperationAndKeepsTheDisk() =
        runTest {
            val stored = InMemoryMailCache()
            var locked = true
            val disk =
                object : MailCache by stored {
                    override suspend fun write(
                        loginId: String,
                        changes: CacheWrite.() -> Unit,
                    ) {
                        if (locked) throw CacheStorageException(IllegalStateException("busy"), transient = true)
                        stored.write(loginId, changes)
                    }
                }
            val cache = RecoveringMailCache(disk)
            assertFailsWith<CacheStorageException> { cache.write("a") { putValue("state", "s1") } }
            locked = false
            cache.write("a") { putValue("state", "s2") }
            assertEquals("s2", stored.value("a", "state"))
        }

    @Test
    fun removalDoesNotHideAFailureToErasePersistentData() =
        runTest {
            val disk =
                object : MailCache by InMemoryMailCache() {
                    override suspend fun forget(loginId: String): Unit =
                        throw CacheStorageException(IllegalStateException("locked"))
                }
            assertFailsWith<CacheStorageException> { RecoveringMailCache(disk).forget("a") }
        }

    @Test
    fun cancellationAndProgrammingErrorsPropagate() =
        runTest {
            val canceled =
                object : MailCache by InMemoryMailCache() {
                    override suspend fun value(
                        loginId: String,
                        key: String,
                    ): String? = throw CancellationException()
                }
            assertFailsWith<CancellationException> { RecoveringMailCache(canceled).value("a", "state") }
            val cache = RecoveringMailCache(InMemoryMailCache())
            assertFailsWith<IllegalArgumentException> { cache.write("a") { require(false) } }
        }
}
