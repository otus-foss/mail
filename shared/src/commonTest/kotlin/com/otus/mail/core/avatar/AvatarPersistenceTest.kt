package com.otus.mail.core.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AvatarPersistenceTest {
    private val image = AvatarImage(byteArrayOf(1, 2, 3))
    private val options = AvatarOptions(domainIcons = true)
    private val request = AvatarRequest("work", "mail@proton.me")

    @Test
    fun concurrentSendersAndAccountsShareOneDomainFetch() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            calls++
                            release.await()
                            image
                        },
                )
            val first = async { service.resolve(request, options) }
            val other = request.copy(email = "asdf@PROTON.me", accountKey = "personal")
            val second = async { service.resolve(other, options) }
            testScheduler.runCurrent()
            assertEquals(1, calls)
            release.complete(Unit)
            assertSame(first.await().image, second.await().image)
        }

    @Test
    fun newServiceRestoresContactPhotosAndSharedIconsWithoutNetwork() =
        runTest {
            val store = TestAvatarStore()
            val service =
                AvatarService(
                    contacts =
                        ContactPhotoSource { sender, _ ->
                            if (sender.email.startsWith("contact")) image else null
                        },
                    images = AvatarImageLoader { image },
                    cacheConfig = AvatarCacheConfig(store = store),
                )
            service.resolve(request, options)
            service.resolve(request.copy(email = "contact@proton.me"), options)
            val restored =
                AvatarService(
                    contacts =
                        ContactPhotoSource { _, _ ->
                            error("Stored contact/miss must be reused")
                        },
                    images = AvatarImageLoader { error("Stored icon must be reused") },
                    cacheConfig = AvatarCacheConfig(store = store),
                )
            assertContentEquals(image.bytes, restored.resolve(request, options).image?.bytes)
            val contact = restored.resolve(request.copy(email = "contact@proton.me"), options)
            assertEquals(AvatarOrigin.Contact, contact.origin)
            val newSender = restored.cached(request.copy(email = "new@proton.me"), options)
            assertContentEquals(image.bytes, newSender?.image?.bytes)
            assertNull(restored.cached(request.copy(email = "new@proton.me")))
        }

    @Test
    fun contactPhotosAtTheSameDomainRemainDistinctAndAccountRemovalRetainsPublicIcons() =
        runTest {
            val other = AvatarImage(byteArrayOf(4))
            val store = TestAvatarStore()
            val service =
                AvatarService(
                    contacts =
                        ContactPhotoSource { sender, _ ->
                            when (sender.email.substringBefore('@')) {
                                "alice" -> image
                                "bob" -> other
                                else -> null
                            }
                        },
                    images = AvatarImageLoader { image },
                    cacheConfig = AvatarCacheConfig(store = store),
                )
            assertSame(image, service.resolve(request.copy(email = "alice@proton.me"), options).image)
            assertSame(other, service.resolve(request.copy(email = "bob@proton.me"), options).image)
            service.resolve(request, options)
            service.removeAccount("work")
            assertEquals(1, store.entries.size)
            assertSame(image, service.cached(request.copy(email = "new@proton.me"), options)?.image)
            assertEquals(AvatarOrigin.Domain, service.cached(request.copy(email = "alice@proton.me"), options)?.origin)
        }

    @Test
    fun existingImagesOutliveMissesAndRemainAvailableDuringAnOfflineRefresh() =
        runTest {
            var now = 0L
            var calls = 0
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            calls++
                            if (calls == 1) image else null
                        },
                    cacheConfig = AvatarCacheConfig(cacheMillis = 1_000_000, nowMillis = { now }),
                )
            service.resolve(request, options)
            now = 300_000
            service.resolve(request.copy(email = "other@proton.me"), options)
            assertEquals(1, calls)
            now = 1_000_000
            assertSame(image, service.cached(request, options)?.image)
            assertSame(image, service.resolve(request, options).image)
        }
}

private class TestAvatarStore : AvatarStore {
    val entries = mutableMapOf<AvatarCacheKey, StoredAvatar>()

    override suspend fun read(key: AvatarCacheKey): StoredAvatar? = entries[key]

    override suspend fun write(
        key: AvatarCacheKey,
        entry: StoredAvatar,
    ) {
        entries[key] = entry
    }

    override suspend fun clear() {
        entries.clear()
    }

    override suspend fun removeAccount(account: String) {
        entries.keys.removeAll { it.account == account }
    }
}
