package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.withFlag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedPendingTest {
    private val serverMail = listOf(message("a"), message("b"))

    /** Answers each load with the snapshot the test completes for it. */
    private class SlowSource : MailSource {
        val answers = ArrayDeque<CompletableDeferred<InboxSnapshot>>()

        override suspend fun messages(scope: MailScope): InboxSnapshot =
            CompletableDeferred<InboxSnapshot>().also { answers.addLast(it) }.await()

        fun answer(messages: List<MailMessage>) {
            answers.removeFirst().complete(InboxSnapshot(messages))
        }
    }

    private fun TestScope.loadedFeed(): Pair<InboxFeedState, SlowSource> {
        val source = SlowSource()
        val feed = InboxFeedState(source, this)
        feed.refresh()
        runCurrent()
        source.answer(serverMail)
        runCurrent()
        return feed to source
    }

    @Test
    fun anArchivedMessageStaysRemovedWhenAnOlderLoadAnswers() =
        runTest {
            val (feed, source) = loadedFeed()
            feed.refresh()
            runCurrent()
            val archive = feed.remove("a")
            source.answer(serverMail)
            runCurrent()
            assertEquals(listOf("b"), feed.messages.map { it.id })

            feed.refresh()
            runCurrent()
            archive.confirm()
            source.answer(serverMail)
            runCurrent()
            assertEquals(listOf("b"), feed.messages.map { it.id }, "The load started before the server saved the move")

            feed.refresh()
            runCurrent()
            source.answer(serverMail)
            runCurrent()
            assertEquals(listOf("a", "b"), feed.messages.map { it.id }, "A load after the move shows the server's mail")
        }

    @Test
    fun aMessageMarkedReadStaysReadWhenAnOlderLoadAnswers() =
        runTest {
            val (feed, source) = loadedFeed()
            feed.refresh()
            runCurrent()
            feed.change("a", { it.withFlag(MessageFlag.Read, true) }, undo = { it.withFlag(MessageFlag.Read, false) })
            source.answer(serverMail)
            runCurrent()
            assertEquals(listOf(false, true), feed.messages.map { it.attributes.unread })
        }

    @Test
    fun aRolledBackChangeLeavesLaterLoadsAlone() =
        runTest {
            val (feed, source) = loadedFeed()
            feed.refresh()
            runCurrent()
            val read =
                feed.change(
                    "a",
                    { it.withFlag(MessageFlag.Read, true) },
                    undo = { it.withFlag(MessageFlag.Read, false) },
                )
            val archive = feed.remove("b")
            read.rollBack()
            archive.rollBack()
            assertEquals(listOf(true, true), feed.messages.map { it.attributes.unread })
            source.answer(serverMail)
            runCurrent()
            assertEquals(listOf("a", "b"), feed.messages.map { it.id })
            assertEquals(listOf(true, true), feed.messages.map { it.attributes.unread })
        }

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Ada", "ada@example.org"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-01", "12:00"),
            MailLocation("Inbox", "Ada"),
            MailAttributes(unread = true),
        )
}
