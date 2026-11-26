package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.avatar.AvatarDomainResolver
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.Unsubscribe
import com.otusfoss.mail.feature.read.domain.UnsubscribeResult
import com.otusfoss.mail.testing.FakeDrafts
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which route leaves a list; `JmapNewslettersTest` covers what the mail route sends. */
class ListUnsubscriberTest {
    private val sent = mutableListOf<Draft>()
    private val posts = mutableListOf<String>()
    private val web = "https://lists.example.org/u/1"

    private val newsletter =
        MailMessage(
            "ada/n5",
            MailSender("Sentry", "weekly@sentry.example"),
            "Your week",
            "",
            ReceivedAt("2026-10-15", "08:00"),
            MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "n5",
            to = listOf(MailSender("Ada", "ada@example.org")),
        )

    private val unsubscriber =
        ListUnsubscriber(
            FakeDrafts(listOf(MailSender("Ada", "ada@example.org", "ada"))),
            DraftSender { draft, _, _ ->
                sent += draft
                Result.success(SentMessage("ada", "s1", "sub1"))
            },
            AvatarDomainResolver { host -> host.split('.').takeLast(2).joinToString(".") },
            OneClickPost(
                MockEngine { request ->
                    posts += request.url.toString()
                    respond("")
                },
            ),
        )

    @Test
    fun aOneClickListWithoutMailGetsThePost() =
        runTest {
            val how = Unsubscribe(web, oneClick = true)
            assertEquals(UnsubscribeResult.Done, unsubscriber.unsubscribe(newsletter, how))
            assertEquals(listOf(web), posts)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun mailGoesFirstSoTheListNeverSeesTheDevice() =
        runTest {
            val how = Unsubscribe(web, mail = "mailto:leave@sentry.example", oneClick = true)
            assertEquals(UnsubscribeResult.Done, unsubscriber.unsubscribe(newsletter, how))
            assertEquals(1, sent.size)
            assertTrue(posts.isEmpty(), "No request leaves the device: $posts")
        }

    @Test
    fun aOneClickListWhoseMailCannotBeSentGetsThePost() =
        runTest {
            val how = Unsubscribe(web, mail = "mailto:leave@elsewhere.example", oneClick = true)
            assertEquals(UnsubscribeResult.Done, unsubscriber.unsubscribe(newsletter, how))
            assertEquals(listOf(web), posts)
            assertTrue(sent.isEmpty(), "Mail outside the list's domain is not sent: $sent")
        }

    @Test
    fun aListThatAsksForNoPostOpensItsPage() =
        runTest {
            assertEquals(UnsubscribeResult.OpenLink(web), unsubscriber.unsubscribe(newsletter, Unsubscribe(web)))
            assertTrue(posts.isEmpty(), "No request leaves the device: $posts")
        }
}
