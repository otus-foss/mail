package com.otus.mail.core.avatar

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarImageLoader
import com.otus.avatar.AvatarOrigin
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ContactPhotoSource
import com.otus.avatar.DomainIconSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MailAvatarOptionsTest {
    private val photo = AvatarImage(byteArrayOf(1))
    private val icon = AvatarImage(byteArrayOf(2))
    private val external = mutableListOf<Boolean>()
    private val urls = mutableListOf<String>()
    private val proxied = mutableListOf<String>()
    private val avatars =
        AvatarService(
            ContactPhotoSource { request, allowExternal ->
                external += allowExternal
                photo.takeIf { request.email == "ada@example.org" }
            },
            AvatarImageLoader {
                urls += it
                null
            },
            iconFallback =
                DomainIconSource {
                    proxied += it
                    icon
                },
        )
    private val ada = AvatarRequest("work", "ada@example.org", "Ada Lovelace")
    private val bob = AvatarRequest("work", "bob@example.org", "Bob Example")

    @Test
    fun withoutWebsiteIconsContactPhotosStillLoadAndNothingElseIsFetched() =
        runTest {
            val off = mailAvatarOptions(websiteIcons = false)
            val found = avatars.resolve(ada, off)
            assertSame(photo, found.image)
            assertEquals(AvatarOrigin.Contact, found.origin)
            assertNull(avatars.resolve(bob, off).image)
            assertEquals(listOf(false, false), external, "Contact cards may not load linked images")
            assertTrue(urls.isEmpty(), "No sender website is asked: $urls")
            assertTrue(proxied.isEmpty(), "DuckDuckGo is not asked: $proxied")
        }

    @Test
    fun withWebsiteIconsSendersWithoutAPhotoTryTheirWebsiteThenDuckDuckGo() =
        runTest {
            val found = avatars.resolve(bob, mailAvatarOptions(websiteIcons = true))
            assertSame(icon, found.image)
            assertEquals(AvatarOrigin.Domain, found.origin)
            assertEquals(listOf(true), external)
            assertTrue(urls.isNotEmpty() && urls.all { it.startsWith("https://example.org/") }, "$urls")
            assertEquals(listOf("example.org"), proxied)
        }

    @Test
    fun websiteIconsFoundBeforeTurningThemOffAreNotShownAfterwards() =
        runTest {
            val on = mailAvatarOptions(websiteIcons = true)
            val off = mailAvatarOptions(websiteIcons = false)
            assertSame(icon, avatars.resolve(bob, on).image)
            val asked = urls.size + proxied.size
            assertNull(avatars.cached(bob, off)?.image)
            assertNull(avatars.resolve(bob, off).image)
            assertEquals(asked, urls.size + proxied.size, "Turning icons off asks no website")
            assertSame(icon, avatars.cached(bob, on)?.image, "Turning them on again reuses the saved icon")
        }
}
