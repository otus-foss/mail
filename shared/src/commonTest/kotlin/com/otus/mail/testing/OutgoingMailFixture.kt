package com.otus.mail.testing

import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.feature.compose.domain.MailOutbox
import com.otus.mail.feature.compose.domain.SendCheck
import com.otus.mail.feature.compose.domain.SendChecker
import com.otus.mail.feature.compose.presentation.Outgoing
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import com.otus.mail.feature.compose.presentation.OutgoingMail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
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

    /** What cancel ends in; the send counts as canceled only when it stopped. */
    protected var cancelOutcome: SendCancel = SendCancel.Stopped
    protected val filedSent = mutableListOf<String>()
    protected val drafts = FakeDrafts()
    protected val checks = mutableListOf<Boolean>()
    protected var failure: SendFailure? = null
    protected var check = SendCheck.NotSent
    protected var hold = HOLD
    protected var serverDelaySeconds = 0L
    protected val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    protected val scheduled =
        object : ScheduledSends {
            override suspend fun maxDelaySeconds(accountId: String) = serverDelaySeconds

            override suspend fun cancel(message: SentMessage): SendCancel {
                if (cancelOutcome == SendCancel.Stopped) cancelled += message
                return cancelOutcome
            }

            override suspend fun sendNow(message: SentMessage): Result<Unit> {
                sentNow += message
                return sendNowFailure?.let { Result.failure(SendException(it)) } ?: Result.success(Unit)
            }

            override suspend fun fileSent(accountId: String) {
                filedSent += accountId
            }

            override suspend fun held(
                accountId: String,
                emailId: String,
            ) = null
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
        return OutgoingMail(backgroundScope, toasts, scheduled, MailOutbox(null, sender, checker, now = now), {
            DAY_FIRST
        }, now) {
            hold
        }
    }

    protected fun swipeAway() = assertNotNull(toasts.messages.single().swipeAction).onClick()

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

/** Dates with the day first, as the toasts were written before they followed the date format. */
internal val DAY_FIRST = DateFormatter(DateFormatPreference.DayMonthYearDots)
