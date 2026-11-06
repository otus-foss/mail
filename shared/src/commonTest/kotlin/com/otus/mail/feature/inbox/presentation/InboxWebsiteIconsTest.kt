package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarImageLoader
import com.otus.avatar.AvatarService
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarOptions
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.testing.photoSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InboxWebsiteIconsTest {
    @Test
    fun turningWebsiteIconsOffDropsShownIconsAndAsksNoWebsiteOnTheNextRefresh() =
        runTest {
            val icon = AvatarImage(byteArrayOf(1))
            val urls = mutableListOf<String>()
            val ownServer = CompletableDeferred<Unit>()
            var websiteIcons = true
            val avatars =
                AvatarService(
                    photoSource { _, external ->
                        if (!external) ownServer.await()
                        null
                    },
                    AvatarImageLoader {
                        urls += it
                        icon
                    },
                )
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(listOf(message()))
                }
            val feed = InboxFeedState(source, this, avatars, { avatarOptions(websiteIcons) })
            feed.refresh()
            testScheduler.runCurrent()
            assertSame(icon, feed.senderAvatar?.image)
            assertSame(icon, feed.accountAvatar?.image)

            websiteIcons = false
            urls.clear()
            feed.refresh()
            testScheduler.runCurrent()
            // The contact lookups on the user's own server are still pending.
            assertNull(feed.senderAvatar, "The sender's website icon is not restored")
            assertNull(feed.accountAvatar, "The account's website icon is not carried over")

            ownServer.complete(Unit)
            testScheduler.runCurrent()
            assertNotNull(feed.senderAvatar)
            assertNull(feed.senderAvatar?.image)
            assertNull(feed.accountAvatar?.image)
            assertTrue(urls.isEmpty(), "No website is asked: $urls")
        }

    @Test
    fun turningWebsiteIconsOnAgainShowsTheSavedIcons() =
        runTest {
            val icon = AvatarImage(byteArrayOf(1))
            var websiteIcons = true
            var iconCalls = 0
            val avatars =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            iconCalls++
                            icon
                        },
                )
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(listOf(message()))
                }
            val feed = InboxFeedState(source, this, avatars, { avatarOptions(websiteIcons) })
            feed.refresh()
            testScheduler.runCurrent()
            websiteIcons = false
            feed.refresh()
            testScheduler.runCurrent()
            assertNull(feed.senderAvatar?.image)

            websiteIcons = true
            feed.refresh()
            testScheduler.runCurrent()
            assertSame(icon, feed.senderAvatar?.image)
            assertSame(icon, feed.accountAvatar?.image)
            assertEquals(2, iconCalls, "Each website was asked once, before icons were turned off")
        }

    @Test
    fun turningWebsiteIconsOffStopsTheWebsiteLookupsStillRunning() =
        runTest {
            val websites = SlowWebsites()
            var websiteIcons = true
            val feed = InboxFeedState(websites.source, this, websites.avatars, { avatarOptions(websiteIcons) })
            feed.refresh()
            testScheduler.runCurrent()
            val asked = websites.urls.size
            assertTrue(asked > 0, "Websites are being asked when the switch changes")

            websiteIcons = false
            // App refreshes the inbox when the switch changes.
            feed.refresh()
            websites.answer()
            testScheduler.advanceUntilIdle()
            assertEquals(asked, websites.urls.size, "No website is asked after the switch is off")
        }

    @Test
    fun leavingAFolderPageStopsItsWebsiteLookups() =
        runTest {
            val websites = SlowWebsites()
            val page = CoroutineScope(StandardTestDispatcher(testScheduler))
            val feed =
                InboxFeedState(websites.source, page, websites.avatars, { avatarOptions(websiteIcons = true) })
            feed.refresh()
            testScheduler.runCurrent()
            val asked = websites.urls.size
            assertTrue(asked > 0, "Websites are being asked when the page leaves")

            page.cancel()
            websites.answer()
            testScheduler.advanceUntilIdle()
            assertEquals(asked, websites.urls.size, "No website is asked after the page left")
        }

    private val InboxFeedState.senderAvatar: ResolvedAvatar?
        get() = messages.single().sender.avatar

    private val InboxFeedState.accountAvatar: ResolvedAvatar?
        get() = messages.single().location.accountAvatar
}

/** Twenty senders, each at its own website, which answers only after [answer]. */
private class SlowWebsites {
    val urls = mutableListOf<String>()
    private val reply = CompletableDeferred<Unit>()
    val avatars =
        AvatarService(
            images =
                AvatarImageLoader {
                    urls += it
                    reply.await()
                    null
                },
        )
    val source =
        object : MailSource {
            override suspend fun messages(scope: MailScope) =
                InboxSnapshot((1..SENDERS).map { message("$it", "sender@site$it.example") })
        }

    fun answer() = reply.complete(Unit)

    private companion object {
        const val SENDERS = 20
    }
}

private fun message(
    id: String = "one",
    address: String = "ada@example.org",
) = MailMessage(
    id,
    MailSender("Ada Lovelace", address, "work"),
    "Subject",
    "Body",
    ReceivedAt("2026-10-05", "09:00"),
    MailLocation("Inbox", "Work", accountAddress = "me@work.org", mailboxRole = MailboxRole.Inbox),
)
