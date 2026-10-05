package com.otus.mail.core.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AvatarServiceConcurrencyTest {
    private val image = AvatarImage(byteArrayOf(1, 2, 3))

    @Test
    fun slowLookupNeverDelaysOtherSenders() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val service =
                AvatarService(
                    ContactPhotoSource { subject, _ ->
                        if (subject.email.startsWith("slow")) release.await()
                        image
                    },
                )
            val slow = async { service.resolve(AvatarRequest("work", "slow@company.example")) }
            yield()
            // Covers every former lock stripe: none of these may wait for the slow sender.
            repeat(SENDERS) {
                assertSame(image, service.resolve(AvatarRequest("work", "person$it@company.example")).image)
            }
            assertFalse(slow.isCompleted)
            release.complete(Unit)
            assertSame(image, slow.await().image)
        }

    @Test
    fun waiterRetriesWhenTheSharedLookupIsCancelled() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            var calls = 0
            val service =
                AvatarService(
                    ContactPhotoSource { _, _ ->
                        calls++
                        if (calls == 1) {
                            entered.complete(Unit)
                            awaitCancellation()
                        }
                        image
                    },
                )
            val request = AvatarRequest("work", "ada@company.example")
            val owner = async { service.resolve(request) }
            entered.await()
            val waiter = async { service.resolve(request) }
            yield()
            owner.cancel()
            assertSame(image, waiter.await().image)
            assertEquals(2, calls)
        }

    @Test
    fun clearingAlsoInvalidatesLookupsWaitingForAFetchPermit() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var queuedCalls = 0
            val service =
                AvatarService(
                    ContactPhotoSource { request, _ ->
                        if (request.email.startsWith("busy")) release.await() else queuedCalls++
                        image
                    },
                )
            val busy =
                List(FETCH_LIMIT) { index ->
                    async { service.resolve(AvatarRequest("work", "busy$index@company.example")) }
                }
            yield()
            val request = AvatarRequest("work", "queued@company.example")
            val queued = async { service.resolve(request) }
            yield()
            assertEquals(0, queuedCalls)
            service.clear()
            release.complete(Unit)
            busy.forEach { it.await() }
            queued.await()
            service.resolve(request)
            assertEquals(2, queuedCalls)
        }

    @Test
    fun unresponsiveWebsitesStopAtTheSharedLimitAndUseTheIconCache() =
        runTest {
            val service =
                AvatarService(
                    images = AvatarImageLoader { awaitCancellation() },
                    domains = AvatarDomainResolver { "company.com" },
                    iconFallback = DomainIconSource { image },
                )
            // Virtual time: six hanging icon requests must give up within the shared limit.
            val result =
                withTimeout(DOMAIN_ICON_LIMIT_MILLIS + 1) {
                    service.resolve(
                        AvatarRequest("work", "noreply@email.company.com"),
                        AvatarOptions(domainIcons = true, domainIconProxy = true),
                    )
                }
            assertSame(image, result.image)
            assertEquals(AvatarOrigin.Domain, result.origin)
        }

    private companion object {
        const val SENDERS = 16
        const val FETCH_LIMIT = 8
        const val DOMAIN_ICON_LIMIT_MILLIS = 8_000L
    }
}
