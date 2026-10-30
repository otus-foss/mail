package com.otus.mail.core.avatar

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarImageLoader
import com.otus.avatar.AvatarOrigin
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
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
            photoSource { request, allowExternal ->
                external += allowExternal
                photo.takeIf { request.email == "ada@example.org" }
            },
            AvatarImageLoader {
                if (it.startsWith("https://example.org/")) {
                    urls += it
                    null
                } else {
                    proxied += it
                    icon
                }
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
            assertTrue(proxied.isEmpty(), "No icon service is asked: $proxied")
        }

    @Test
    fun withWebsiteIconsAloneSendersWithoutAPhotoTryOnlyTheirWebsite() =
        runTest {
            val found = avatars.resolve(bob, mailAvatarOptions(websiteIcons = true))
            assertNull(found.image)
            assertTrue(urls.isNotEmpty() && urls.all { it.startsWith("https://example.org/") }, "$urls")
            assertTrue(proxied.isEmpty(), "No icon service is asked: $proxied")
        }

    @Test
    fun iconServicesAreAskedOnlyWithWebsiteIcons() =
        runTest {
            assertNull(avatars.resolve(bob, mailAvatarOptions(websiteIcons = false, IconSource.DuckDuckGo)).image)
            assertTrue(urls.isEmpty() && proxied.isEmpty(), "Nothing is asked: $urls $proxied")
        }

    @Test
    fun duckDuckGoIsAskedInsteadOfTheWebsite() =
        runTest {
            val found = avatars.resolve(bob, mailAvatarOptions(websiteIcons = true, IconSource.DuckDuckGo))
            assertSame(icon, found.image)
            assertEquals(AvatarOrigin.Domain, found.origin)
            assertEquals(listOf(true), external)
            assertTrue(urls.isEmpty(), "No sender website is asked: $urls")
            assertEquals(listOf("https://icons.duckduckgo.com/ip3/example.org.ico"), proxied)
        }

    @Test
    fun bulwarkIsAskedAtItsAddressAndShowsNothingWithoutOne() =
        runTest {
            val bulwark = mailAvatarOptions(websiteIcons = true, IconSource.Bulwark, "mail.example.net/")
            assertSame(icon, avatars.resolve(bob, bulwark).image)
            assertEquals(listOf("https://mail.example.net/api/favicon?domain=example.org"), proxied)
            val carol = AvatarRequest("work", "carol@example.com")
            for (address in listOf("", "http://mail.example.net")) {
                assertNull(avatars.resolve(carol, mailAvatarOptions(true, IconSource.Bulwark, address)).image)
            }
            assertTrue(urls.isEmpty(), "No sender website is asked: $urls")
            assertEquals(1, proxied.size)
        }

    @Test
    fun bulwarkAddressesMustBePublicHttpsWithoutExtraParts() {
        assertEquals("https://mail.example.net", bulwarkBase(" https://mail.example.net/ "))
        listOf(
            "",
            "http://mail.example.net",
            "mail.example.net:8443",
            "mail.example.net?x=1",
            "mail.example.net#top",
            "mail.example.net/{domain}",
            "user@mail.example.net",
            "localhost",
            "192.168.1.2",
            "nas.lan",
        ).forEach { assertNull(bulwarkBase(it), it) }
    }

    @Test
    fun websiteIconsFoundBeforeTurningThemOffAreNotShownAfterwards() =
        runTest {
            val on = mailAvatarOptions(websiteIcons = true, IconSource.DuckDuckGo)
            val off = mailAvatarOptions(websiteIcons = false)
            assertSame(icon, avatars.resolve(bob, on).image)
            val asked = urls.size + proxied.size
            assertNull(avatars.cached(bob, off)?.image)
            assertNull(avatars.resolve(bob, off).image)
            assertEquals(asked, urls.size + proxied.size, "Turning icons off asks no website")
            assertSame(icon, avatars.cached(bob, on)?.image, "Turning them on again reuses the saved icon")
        }
}
