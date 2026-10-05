package com.otus.mail.core.avatar

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AvatarDomainIconTest {
    private val request = AvatarRequest("work", "noreply@email.openai.com", "OpenAI")
    private val image = AvatarImage(byteArrayOf(1, 2, 3))
    private val options = AvatarOptions(domainIcons = true, domainIconProxy = true)
    private val domains = PublicSuffixDomainResolver { "com\nme\nio\ngithub.io" }

    @Test
    fun missingSubdomainIconFallsBackToRootAndIconCacheReceivesOnlyTheRoot() =
        runTest {
            val urls = mutableListOf<String>()
            val cachedDomains = mutableListOf<String>()
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            null
                        },
                    domains = domains,
                    iconFallback =
                        DomainIconSource {
                            cachedDomains += it
                            image
                        },
                )
            val result = service.resolve(request, options)
            assertEquals(
                listOf(
                    "https://email.openai.com/apple-touch-icon.png",
                    "https://email.openai.com/favicon.png",
                    "https://email.openai.com/favicon.ico",
                    "https://openai.com/apple-touch-icon.png",
                    "https://openai.com/favicon.png",
                    "https://openai.com/favicon.ico",
                ),
                urls,
            )
            assertEquals(listOf("openai.com"), cachedDomains)
            assertSame(image, result.image)
            assertEquals(AvatarOrigin.Domain, result.origin)
        }

    @Test
    fun subdomainIconWinsWithoutFetchingTheRootOrIconCache() =
        runTest {
            val urls = mutableListOf<String>()
            var cacheCalls = 0
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            if (it == "https://email.openai.com/favicon.ico") image else null
                        },
                    domains = domains,
                    iconFallback =
                        DomainIconSource {
                            cacheCalls++
                            image
                        },
                )
            assertSame(image, service.resolve(request, options).image)
            assertEquals(
                listOf(
                    "https://email.openai.com/apple-touch-icon.png",
                    "https://email.openai.com/favicon.png",
                    "https://email.openai.com/favicon.ico",
                ),
                urls,
            )
            assertEquals(0, cacheCalls)
        }

    @Test
    fun failedSubdomainRequestsUseTheRootWebsiteIconWithoutAnIconCache() =
        runTest {
            val urls = mutableListOf<String>()
            var cacheCalls = 0
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            if (it.contains("email.openai.com")) error("Subdomain unreachable")
                            if (it == "https://openai.com/favicon.ico") image else null
                        },
                    domains = domains,
                    iconFallback =
                        DomainIconSource {
                            cacheCalls++
                            image
                        },
                )
            assertSame(image, service.resolve(request, options).image)
            assertEquals("https://openai.com/favicon.ico", urls.last())
            assertEquals(6, urls.size)
            assertEquals(0, cacheCalls)
        }

    @Test
    fun protonWebsiteIconIsUsedWithoutAnIconCacheRequest() =
        runTest {
            val urls = mutableListOf<String>()
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            if (it.endsWith("favicon.ico")) image else null
                        },
                    domains = domains,
                    iconFallback = DomainIconSource { error("Direct website icon is available") },
                )
            val result = service.resolve(request.copy(email = "ada@proton.me"), options)
            assertSame(image, result.image)
            assertEquals("https://proton.me/favicon.ico", urls.last())
            assertEquals(3, urls.size)
        }

    @Test
    fun iconCacheRequiresBothDomainIconsAndItsSeparateOption() =
        runTest {
            var calls = 0
            val service =
                AvatarService(
                    domains = domains,
                    iconFallback =
                        DomainIconSource {
                            calls++
                            image
                        },
                )
            assertNull(service.resolve(request, options.copy(domainIconProxy = false)).image)
            assertNull(service.resolve(request, options.copy(domainIcons = false)).image)
            assertEquals(0, calls)
            assertSame(image, service.resolve(request, options).image)
            assertEquals(1, calls)
        }

    @Test
    fun contactPhotoWinsBeforeDomainResolutionOrAnIconCacheRequest() =
        runTest {
            var calls = 0
            val service =
                AvatarService(
                    contacts = ContactPhotoSource { _, _ -> image },
                    domains =
                        AvatarDomainResolver {
                            calls++
                            it
                        },
                    iconFallback =
                        DomainIconSource {
                            calls++
                            image
                        },
                )
            val result = service.resolve(request, options)
            assertSame(image, result.image)
            assertEquals(AvatarOrigin.Contact, result.origin)
            assertEquals(0, calls)
        }

    @Test
    fun publicSuffixesAndUnrelatedResolvedDomainsAreNotFetched() =
        runTest {
            var calls = 0
            val service =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            calls++
                            image
                        },
                    domains = domains,
                    iconFallback =
                        DomainIconSource {
                            calls++
                            image
                        },
                )
            assertNull(service.resolve(request.copy(email = "ada@github.io"), options).image)
            val unrelated =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            calls++
                            image
                        },
                    domains = AvatarDomainResolver { "other.example" },
                    iconFallback =
                        DomainIconSource {
                            calls++
                            image
                        },
                )
            assertNull(unrelated.resolve(request, options).image)
            assertEquals(0, calls)
        }
}
