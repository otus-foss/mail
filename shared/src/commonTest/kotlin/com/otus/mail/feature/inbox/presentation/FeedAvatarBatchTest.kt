package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarService
import com.otus.avatar.avatarOptions
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.photoSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Avatars that arrive together change the feed's list once. */
class FeedAvatarBatchTest {
    @Test
    fun avatarsThatArriveTogetherChangeTheListOnce() =
        runTest {
            val image = AvatarImage(byteArrayOf(3))
            val avatars = AvatarService(photoSource { _, _ -> image })
            var messages = listOf(message("ada"), message("grace"))
            var updates = 0
            FeedAvatars(avatars).resolve(messages, avatarOptions(websiteIcons = false), { true }) { edit ->
                updates++
                messages = messages.map(edit)
            }
            assertEquals(1, updates, "Both senders' images change the list in one go")
            assertTrue(messages.all { it.sender.avatar?.image === image })
        }

    @Test
    fun avatarsForALoadThatIsNoLongerShownChangeNothing() =
        runTest {
            val avatars = AvatarService(photoSource { _, _ -> AvatarImage(byteArrayOf(4)) })
            var updates = 0
            FeedAvatars(avatars).resolve(listOf(message("ada")), avatarOptions(websiteIcons = false), { false }) {
                updates++
            }
            assertEquals(0, updates)
        }

    private fun message(name: String) =
        MailMessage(
            name,
            MailSender(name, "$name@example.org", "work"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-05", "09:00"),
            MailLocation("Inbox", "Work"),
        )
}
