package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.AvatarCacheConfig
import com.otus.avatar.AvatarCacheKey
import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarImageLoader
import com.otus.avatar.AvatarOrigin
import com.otus.avatar.AvatarService
import com.otus.avatar.AvatarStore
import com.otus.avatar.StoredAvatar
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.photoSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InboxAvatarTest {
    @Test
    fun missingContactAutomaticallyUsesTheDomainIconInMail() =
        runTest {
            val image = AvatarImage(byteArrayOf(1))
            val urls = mutableListOf<String>()
            val avatars =
                AvatarService(
                    photoSource { _, external ->
                        assertTrue(external)
                        null
                    },
                    AvatarImageLoader {
                        urls += it
                        image
                    },
                )
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(listOf(message("one")))
                }
            val feed = InboxFeedState(source, this, avatars)
            feed.refresh()
            testScheduler.runCurrent()
            assertEquals(listOf("https://example.org/apple-touch-icon.png"), urls)
            assertSame(
                image,
                feed.messages
                    .single()
                    .sender.avatar
                    ?.image,
            )
            assertEquals(
                AvatarOrigin.Domain,
                feed.messages
                    .single()
                    .sender.avatar
                    ?.origin,
            )
        }

    @Test
    fun inboxAppearsBeforePhotosAndRepeatedSendersShareTheResolvedImage() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val image = AvatarImage(byteArrayOf(1))
            var calls = 0
            val avatars =
                AvatarService(
                    photoSource { _, _ ->
                        calls++
                        entered.complete(Unit)
                        release.await()
                        image
                    },
                )
            val messages = listOf(message("one"), message("two"))
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(messages)
                }
            val feed = InboxFeedState(source, this, avatars)
            feed.refresh()
            entered.await()
            assertEquals(messages, feed.messages)
            assertFalse(feed.loading)
            assertNull(
                feed.messages
                    .first()
                    .sender.avatar,
            )
            release.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(1, calls)
            feed.messages.forEach { assertSame(image, it.sender.avatar?.image) }
        }

    @Test
    fun refreshKeepsImagesAndNewSendersAtAKnownDomainAppearWithItsCachedIcon() =
        runTest {
            val image = AvatarImage(byteArrayOf(1))
            val release = CompletableDeferred<Unit>()
            var contactCalls = 0
            var iconCalls = 0
            val avatars =
                AvatarService(
                    photoSource { sender, _ ->
                        contactCalls++
                        if (sender.email.startsWith("new")) release.await()
                        null
                    },
                    AvatarImageLoader {
                        iconCalls++
                        image
                    },
                )
            var incoming = listOf(message("one"))
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(incoming)
                }
            val feed = InboxFeedState(source, this, avatars)
            feed.refresh()
            testScheduler.runCurrent()
            incoming =
                listOf(
                    message("one").copy(sender = MailSender("Augusta Ada", "ADA@example.org", "work")),
                    message("two").copy(sender = MailSender("New Person", "new@example.org", "work")),
                )
            feed.refresh()
            testScheduler.runCurrent()
            // The new sender's contact lookup is pending; both rows already have the cached domain image.
            assertFalse(feed.loading)
            feed.messages.forEach { assertSame(image, it.sender.avatar?.image) }
            assertEquals(
                "AA",
                feed.messages
                    .first()
                    .sender.avatar
                    ?.fallback
                    ?.initials,
            )
            assertEquals(2, contactCalls)
            assertEquals(1, iconCalls)
            release.complete(Unit)
            testScheduler.runCurrent()
            feed.refresh()
            testScheduler.runCurrent()
            assertEquals(2, contactCalls)
            assertEquals(1, iconCalls)
            feed.messages.forEach { assertSame(image, it.sender.avatar?.image) }
        }

    @Test
    fun restoringEvictedBytesRetainsTheDisplayedImageAndAvoidsAnotherDecode() =
        runTest {
            val entries = mutableMapOf<AvatarCacheKey, StoredAvatar>()
            val store =
                object : AvatarStore {
                    override suspend fun read(key: AvatarCacheKey): StoredAvatar? =
                        entries[key]?.let {
                            it.copy(
                                image =
                                    it.image
                                        ?.bytes
                                        ?.copyOf()
                                        ?.let(::AvatarImage),
                            )
                        }

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
            var iconCalls = 0
            val avatars =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            iconCalls++
                            AvatarImage(byteArrayOf(1, 2, 3))
                        },
                    cacheConfig = AvatarCacheConfig(maxEntries = 1, store = store),
                )
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(listOf(message("one")))
                }
            val feed = InboxFeedState(source, this, avatars)
            feed.refresh()
            testScheduler.runCurrent()
            val original =
                feed.messages
                    .single()
                    .sender.avatar
                    ?.image
            feed.refresh()
            testScheduler.runCurrent()
            assertSame(
                original,
                feed.messages
                    .single()
                    .sender.avatar
                    ?.image,
            )
            assertEquals(1, iconCalls)
        }

    @Test
    fun accountAddressResolvesItsOwnAvatarForEveryMessageInTheAccount() =
        runTest {
            val image = AvatarImage(byteArrayOf(2))
            val avatars =
                AvatarService(
                    photoSource { request, _ -> image.takeIf { request.email == "me@work.example" } },
                )
            val location =
                MailLocation("Inbox", "Work", accountAddress = "me@work.example", mailboxRole = MailboxRole.Inbox)
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) =
                        InboxSnapshot(listOf(message("one"), message("two")).map { it.copy(location = location) })
                }
            val feed = InboxFeedState(source, this, avatars)
            feed.refresh()
            testScheduler.runCurrent()
            assertTrue(feed.messages.all { it.location.accountAvatar?.image === image })
        }

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Ada Lovelace", "ada@example.org", "work"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-05", "09:00"),
            MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
        )
}
