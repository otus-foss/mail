package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.core.avatar.AvatarImage
import com.otusfoss.core.avatar.AvatarService
import com.otusfoss.core.avatar.avatarOptions
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.presentation.avatar
import com.otusfoss.mail.testing.photoSource
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
            MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
        )
}
