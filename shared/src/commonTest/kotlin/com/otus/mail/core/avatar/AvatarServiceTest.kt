package com.otus.mail.core.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AvatarServiceTest {
    private val request = AvatarRequest("work", "ada@company.example", "Ada Lovelace")
    private val image = AvatarImage(byteArrayOf(1, 2, 3))

    @Test
    fun contactWinsWithoutRequestingDomainIcons() =
        runTest {
            val service =
                AvatarService(
                    ContactPhotoSource {
                        _,
                        _,
                        ->
                        image
                    },
                    AvatarImageLoader { error("No domain request") },
                )
            val result = service.resolve(request, AvatarOptions(domainIcons = true))
            assertSame(image, result.image)
            assertEquals(AvatarOrigin.Contact, result.origin)
            assertEquals("AL", result.fallback.initials)
        }

    @Test
    fun externalRequestsAreDisabledByDefault() =
        runTest {
            val service =
                AvatarService(
                    ContactPhotoSource { _, external ->
                        assertEquals(false, external)
                        null
                    },
                    AvatarImageLoader { error("External fetch disabled") },
                )
            assertNull(service.resolve(request).image)
        }

    @Test
    fun missingContactTriesDomainPathsUntilOneWorks() =
        runTest {
            val urls = mutableListOf<String>()
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            if (it.endsWith("favicon.png")) image else null
                        },
                )
            val result = service.resolve(request, AvatarOptions(domainIcons = true))
            assertEquals(
                listOf("https://company.example/apple-touch-icon.png", "https://company.example/favicon.png"),
                urls,
            )
            assertEquals(AvatarOrigin.Domain, result.origin)
        }

    @Test
    fun failuresRetainInitialsAndCancellationPropagates() =
        runTest {
            val failed = AvatarService(ContactPhotoSource { _, _ -> error("Offline") })
            assertNull(failed.resolve(request).image)
            val cancelled = AvatarService(ContactPhotoSource { _, _ -> throw CancellationException("Refresh") })
            assertFailsWith<CancellationException> { cancelled.resolve(request) }
        }

    @Test
    fun cacheSeparatesAccountsPoliciesAndEmailAliases() =
        runTest {
            val calls = mutableListOf<AvatarRequest>()
            val service =
                AvatarService(
                    ContactPhotoSource { subject, _ ->
                        calls += subject
                        image
                    },
                )
            service.resolve(request)
            service.resolve(request.copy(email = " ada@COMPANY.EXAMPLE ", name = "Augusta Ada"))
            service.resolve(request.copy(accountKey = "personal"))
            service.resolve(request, AvatarOptions(externalContactImages = true))
            service.resolve(request.copy(email = "ada+work@company.example"))
            service.resolve(request.copy(email = "Ada@company.example"))
            assertEquals(4, calls.size)
            assertEquals("AA", service.resolve(request.copy(name = "Augusta Ada")).fallback.initials)
        }

    @Test
    fun cacheIncludesMissingPhotosExpiresAndEvictsOldEntries() =
        runTest {
            var now = 0L
            var calls = 0
            val service =
                AvatarService(
                    ContactPhotoSource { _, _ ->
                        calls++
                        null
                    },
                    maxEntries = 1,
                    cacheMillis = 100,
                    nowMillis = { now },
                )
            service.resolve(request)
            service.resolve(request)
            assertEquals(1, calls)
            now = 100
            service.resolve(request)
            assertEquals(2, calls)
            service.resolve(request.copy(accountKey = "other"))
            service.resolve(request)
            assertEquals(4, calls)
            service.clear()
            service.resolve(request)
            assertEquals(5, calls)
        }

    @Test
    fun simultaneousRequestsShareOneLookup() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val service =
                AvatarService(
                    ContactPhotoSource {
                        _,
                        _,
                        ->
                        calls++
                        entered.complete(Unit)
                        release.await()
                        image
                    },
                )
            val first = async { service.resolve(request) }
            entered.await()
            val second = async { service.resolve(request) }
            release.complete(Unit)
            assertSame(first.await().image, second.await().image)
            assertEquals(1, calls)
        }

    @Test
    fun clearingDuringLookupPreventsCachingTheOldResult() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val service =
                AvatarService(
                    ContactPhotoSource {
                        _,
                        _,
                        ->
                        calls++
                        entered.complete(Unit)
                        release.await()
                        image
                    },
                )
            val pending = async { service.resolve(request) }
            entered.await()
            service.clear()
            release.complete(Unit)
            pending.await()
            service.resolve(request)
            assertEquals(2, calls)
        }

    @Test
    fun generatedIdentityIsStableAndInitialsFollowTheCurrentName() {
        val original = generatedAvatar("Ada Lovelace", "ada@company.example")
        assertEquals(original.colorIndex, generatedAvatar("Augusta Ada", "ada@company.example").colorIndex)
        assertEquals(original, generatedAvatar("Ada Lovelace", " ada@COMPANY.EXAMPLE "))
        assertEquals("A", generatedAvatar("", "ada@company.example").initials)
        assertEquals("?", generatedAvatar("", "").initials)
        assertNotEquals(avatarEmailKey("a.da@company.example"), avatarEmailKey("ada@company.example"))
        assertEquals(original, generatedAvatar("Ada Lovelace", "Ada@Company.Example"))
    }

    @Test
    fun unsafeDomainsHaveNoDomainIconAndPublicMailProvidersAreAllowed() {
        listOf(
            "ada@localhost",
            "ada@127.0.0.1",
            "ada@a.local",
            "ada@company.example/path",
            "ada@a..com",
            "ada@-a.com",
            "ada@@company.example",
            "",
        ).forEach {
            assertNull(avatarIconDomain(it), it)
        }
        assertEquals("company.example", avatarIconDomain("ada@COMPANY.EXAMPLE"))
        assertEquals("proton.me", avatarIconDomain("ada@proton.me"))
        assertEquals("gmail.com", avatarIconDomain("ada@gmail.com"))
        assertEquals("outlook.com", avatarIconDomain("ada@outlook.com"))
    }

    @Test
    fun missingAddressDoesNotTriggerAnyLookup() =
        runTest {
            val service = AvatarService(ContactPhotoSource { _, _ -> error("Missing email") })
            assertEquals("AL", service.resolve(request.copy(email = "")).fallback.initials)
        }
}
