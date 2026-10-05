package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.avatar.AvatarImage
import com.otus.mail.core.avatar.AvatarImageLoader
import com.otus.mail.core.avatar.AvatarOrigin
import com.otus.mail.core.avatar.AvatarService
import com.otus.mail.core.avatar.ContactPhotoSource
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
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
                    ContactPhotoSource { _, external ->
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
                    override suspend fun inbox() = InboxSnapshot(listOf(message("one")))
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
                    ContactPhotoSource { _, _ ->
                        calls++
                        entered.complete(Unit)
                        release.await()
                        image
                    },
                )
            val messages = listOf(message("one"), message("two"))
            val source =
                object : MailSource {
                    override suspend fun inbox() = InboxSnapshot(messages)
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

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Ada Lovelace", "ada@example.org", "work"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-05", "09:00"),
            MailLocation("Inbox", "Work"),
        )
}
