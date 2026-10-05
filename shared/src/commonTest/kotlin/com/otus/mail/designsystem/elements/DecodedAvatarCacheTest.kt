package com.otus.mail.designsystem.elements

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DecodedAvatarCacheTest {
    private val bytes = byteArrayOf(1)
    private val image = AvatarTestImage()

    @Test
    fun concurrentRowsShareOneDecodeAndReuseTheResult() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val cache =
                DecodedAvatarCache {
                    calls++
                    release.await()
                    image
                }
            val first = async { cache.load(bytes) }
            yield()
            val second = async { cache.load(bytes) }
            yield()
            assertEquals(1, calls)
            release.complete(Unit)
            assertSame(image, first.await())
            assertSame(image, second.await())
            assertSame(image, cache.load(bytes))
            assertEquals(1, calls)
        }

    @Test
    fun remainingRowRetriesWhenTheDecodeOwnerLeaves() =
        runTest {
            var calls = 0
            val cache =
                DecodedAvatarCache {
                    calls++
                    if (calls == 1) awaitCancellation()
                    image
                }
            val owner = async { cache.load(bytes) }
            yield()
            val waiter = async { cache.load(bytes) }
            yield()
            owner.cancel()
            assertSame(image, waiter.await())
            assertEquals(2, calls)
        }

    @Test
    fun malformedImagesStayCachedAsMisses() =
        runTest {
            var calls = 0
            val cache =
                DecodedAvatarCache {
                    calls++
                    null
                }
            repeat(2) { assertNull(cache.load(bytes)) }
            assertEquals(1, calls)
        }

    @Test
    fun recentlyUsedImagesSurviveEviction() =
        runTest {
            val calls = mutableListOf<ByteArray>()
            val cache =
                DecodedAvatarCache(maxEntries = 2) {
                    calls += it
                    image
                }
            val other = byteArrayOf(2)
            cache.load(bytes)
            cache.load(other)
            assertSame(image, cache.get(bytes))
            cache.load(byteArrayOf(3))
            cache.load(bytes)
            assertEquals(3, calls.size)
            cache.load(other)
            assertEquals(4, calls.size)
            assertSame(other, calls.last())
        }
}
