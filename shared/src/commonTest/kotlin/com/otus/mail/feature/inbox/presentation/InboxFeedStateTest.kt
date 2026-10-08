package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedStateTest {
    @Test
    fun storageFailurePreservesMessagesAndRetryClearsTheError() =
        runTest {
            val message =
                MailMessage(
                    "a",
                    MailSender("Ada", "ada@example.org"),
                    "Subject",
                    "Body",
                    ReceivedAt("2026-10-01", "12:00"),
                    MailLocation("Inbox", "Ada"),
                )
            var fail = false
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        if (fail) throw InboxStorageException(IllegalStateException("read failed"))
                        return InboxSnapshot(listOf(message))
                    }
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            fail = true
            state.refresh()
            runCurrent()
            assertEquals(listOf(message), state.messages)
            assertNotNull(state.notice)
            assertFalse(state.loading)
            fail = false
            state.refresh()
            runCurrent()
            assertNull(state.notice)
            assertFalse(state.loading)
        }

    @Test
    fun refreshCancelsThePreviousRequestWithoutReportingAnError() =
        runTest {
            var calls = 0
            var cancelled = false
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        if (++calls == 1) {
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled = true
                            }
                        }
                        return InboxSnapshot(emptyList())
                    }
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            state.refresh()
            runCurrent()
            assertEquals(true, cancelled)
            assertNull(state.notice)
            assertFalse(state.loading)
        }
}
