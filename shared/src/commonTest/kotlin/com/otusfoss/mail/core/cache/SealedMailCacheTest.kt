package com.otusfoss.mail.core.cache

import com.otusfoss.core.storage.MarkingTextCipher
import com.otusfoss.core.storage.isSealedText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SealedMailCacheTest : MailCacheContractTest() {
    private val kept = InMemoryMailCache()
    private val cipher = MarkingTextCipher()

    override fun newCache(): MailCache = SealedMailCache(InMemoryMailCache(), cipher)

    @Test
    fun whatIsKeptIsSealed() =
        runTest {
            SealedMailCache(kept, cipher).write("ada") {
                put(CacheKind.Body, "e1", "the body")
                putValue("Email", "s1")
            }

            assertTrue(isSealedText(kept.readAll("ada", CacheKind.Body).getValue("e1")))
            assertTrue(isSealedText(checkNotNull(kept.value("ada", "Email"))))
        }

    @Test
    fun aRowKeptUnsealedOrCopiedFromAnotherRowReadsAsMissing() =
        runTest {
            val cache = SealedMailCache(kept, cipher)
            cache.write("ada") {
                put(CacheKind.Email, "e1", "first")
                putValue("Email", "s1")
            }
            val first = kept.readAll("ada", CacheKind.Email).getValue("e1")
            val state = checkNotNull(kept.value("ada", "Email"))
            kept.write("ada") {
                put(CacheKind.Email, "e2", first)
                put(CacheKind.Email, "e3", "plain")
                putValue("Thread", state)
            }
            kept.write("bob") { put(CacheKind.Email, "e1", first) }

            assertEquals(mapOf("e1" to "first"), cache.readAll("ada", CacheKind.Email))
            assertEquals(emptyMap(), cache.read("ada", CacheKind.Email, listOf("e2", "e3")))
            assertEquals(emptyMap(), cache.readAll("bob", CacheKind.Email))
            assertNull(cache.value("ada", "Thread"))
        }
}
