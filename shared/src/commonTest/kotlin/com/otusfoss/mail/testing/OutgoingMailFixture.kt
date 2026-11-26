package com.otusfoss.mail.testing

import com.otusfoss.core.dateformat.DateFormatPreference
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import com.otusfoss.mail.feature.compose.domain.SendCheck
import com.otusfoss.mail.feature.compose.domain.SendChecker
import com.otusfoss.mail.feature.compose.presentation.Outgoing
import com.otusfoss.mail.feature.compose.presentation.OutgoingDraft
import com.otusfoss.mail.feature.compose.presentation.OutgoingMail
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
    protected val scheduled = FakeScheduledSends()
    protected val cancelled get() = scheduled.cancelled
    protected val sentNow get() = scheduled.sentNow
    protected var sendNowFailure by scheduled::sendNowFailure
    protected var cancelOutcome by scheduled::cancelOutcome
    protected val filedSent get() = scheduled.filedSent
    protected val drafts = FakeDrafts()
    protected val checks = mutableListOf<Boolean>()
    protected var failure: SendFailure? = null
    protected var check = SendCheck.NotSent
    protected var hold = HOLD
    protected var serverDelaySeconds by scheduled::maxDelay
    protected val draft = Draft("a1", subject = "Lunch", emailId = "d1")

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
        val outbox = MailOutbox(null, sender, checker, scheduled, now = now)
        return OutgoingMail(backgroundScope, toasts, scheduled, outbox, { DAY_FIRST }, now) { hold }
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
        val NOW = Instant.parse("2026-10-16T12:00:00Z")
    }
}

/** Dates with the day first, as the toasts were written before they followed the date format. */
internal val DAY_FIRST = DateFormatter(DateFormatPreference.DayMonthYearDots)
