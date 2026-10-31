package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailOutbox
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendCheck
import com.otus.mail.feature.inbox.domain.SendChecker
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SendTime
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.presentation.OutgoingDraft
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What the tests of [OutgoingMail] share: a fake sender, server holds, and the toasts they see. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class OutgoingMailFixture {
    protected val toasts = ToastState()
    protected val runs = mutableListOf<SendTime>()
    protected val opened = mutableListOf<ComposeRequest>()
    protected val cancelled = mutableListOf<SentMessage>()
    protected val sentNow = mutableListOf<SentMessage>()
    protected var sendNowFailure: SendFailure? = null
    protected val checks = mutableListOf<Boolean>()
    protected var failure: SendFailure? = null
    protected var check = SendCheck.NotSent
    protected var hold = HOLD
    protected var serverDelaySeconds = 0L
    protected val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    protected val scheduled =
        object : ScheduledSends {
            override suspend fun maxDelaySeconds(accountId: String) = serverDelaySeconds

            override suspend fun cancel(message: SentMessage) = Result.success(Unit).also { cancelled += message }

            override suspend fun sendNow(message: SentMessage): Result<Unit> {
                sentNow += message
                return sendNowFailure?.let { Result.failure(SendException(it)) } ?: Result.success(Unit)
            }
        }

    protected val checker =
        SendChecker { _, _, cancelHeld ->
            checks += cancelHeld
            check
        }

    protected val sender =
        DraftSender { _, _, time ->
            runs += time
            failure
                ?.let { Result.failure<SentMessage>(SendException(it)) }
                ?: Result.success(SentMessage("a1", "e9", "s1"))
        }

    protected fun TestScope.outgoing(): OutgoingMail {
        val now = { NOW + testScheduler.currentTime.milliseconds }
        return OutgoingMail(backgroundScope, toasts, scheduled, MailOutbox(null, sender, checker, now = now), now) {
            hold
        }
    }

    protected fun swipeAway() = assertNotNull(toasts.messages.single().swipeAction).onAction()

    protected val message =
        Outgoing(
            OutgoingDraft(ready = { draft }, answered = null, sent = {}),
            reopen = ComposeRequest.Resume(draft, ComposeRequest.New()),
            open = { opened += it },
        )

    protected companion object {
        const val HOLD = 5_000L
        val NOW = Instant.parse("2026-10-30T12:00:00Z")
    }
}
