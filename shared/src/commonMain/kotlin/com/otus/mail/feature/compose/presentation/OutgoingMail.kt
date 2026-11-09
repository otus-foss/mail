package com.otus.mail.feature.compose.presentation

import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.DraftRestoreException
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.feature.compose.domain.MailOutbox
import com.otus.mail.feature.compose.domain.OutboxProblem
import com.otus.mail.feature.compose.presentation.components.scheduledToast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Sends drafts after the compose page has closed, with toasts that say how it went. Each draft goes
 * into [outbox] first, so it still goes if the app closes. A message waits [holdMillis], read when
 * it is sent, with Undo, which opens it again; a hold of 0 sends at once. Where the server can hold
 * mail that long, the message goes to the server at once and the server holds it; Undo cancels it
 * there. Elsewhere the device holds it. Swiping the toast away sends it at once. A scheduled one
 * goes to the server at once too. A failure stays as a toast with Retry.
 */
internal class OutgoingMail(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val outbox: MailOutbox,
    private val dates: () -> DateFormatter,
    private val now: () -> Instant = { Clock.System.now() },
    private val holdMillis: () -> Long,
) {
    fun send(outgoing: Outgoing) {
        val group = Any()
        val hold = holdMillis()
        val deadline = now() + hold.milliseconds
        val id = scope.async { park(outgoing, deadline) }
        if (hold == 0L) {
            scope.launch {
                toasts.show(ToastMessage("Sending…", group = group))
                deliver(outgoing, id.await(), group)
            }
            return
        }
        val held = scope.async { holdOnServer(outgoing, id.await(), hold, group) }
        val early = CompletableDeferred<Unit>()
        val onDevice =
            scope.launch {
                if (held.await() != null) return@launch
                withTimeoutOrNull(hold) { early.await() }
                toasts.show(ToastMessage("Sending…", group = group))
                deliver(outgoing, id.await(), group)
            }
        val undo =
            UiAction("Undo") {
                onDevice.cancel()
                scope.launch {
                    val sent = held.await()?.getOrNull()
                    when {
                        sent != null -> unschedule(outgoing, sent)
                        outbox.cancel(id.await()) -> outgoing.open(outgoing.reopen)
                        else -> toasts.show(ToastMessage("Already sent", group = group))
                    }
                }
            }
        val sentToast = { scope.launch { if (held.await()?.isSuccess == true) toasts.show(sent(group)) } }
        val send =
            UiAction("Send") {
                early.complete(Unit)
                scope.launch { sendEarly(outgoing, held.await()?.getOrNull() ?: return@launch, deadline, group) }
            }
        toasts.show(
            ToastMessage("Sending…", undo, group, onClose = { sentToast() }, durationMillis = hold, swipeAction = send),
        )
    }

    fun schedule(
        outgoing: Outgoing,
        at: LocalDateTime,
    ) {
        scope.launch {
            val id = park(outgoing, now(), at.toInstant(TimeZone.currentSystemDefault()))
            schedule(outgoing, id, at)
        }
    }

    /**
     * Sends what the outbox kept from an earlier run, such as a message held on the device when
     * the app closed, and offers each message that needs the user, which [open] shows. Opening
     * one takes it out of the outbox once [open] returns, as a page has taken it.
     */
    fun resume(open: suspend (ComposeRequest) -> Unit) {
        scope.launch {
            outbox.resume()
            outbox.offers().forEach { entry ->
                val draft = entry.draft.copy(messageId = null)
                val action =
                    UiAction("Open") {
                        scope.launch {
                            open(ComposeRequest.Resume(draft, ComposeRequest.New(draft.accountId)))
                            outbox.drop(entry.id)
                        }
                    }
                toasts.show(ToastMessage(entry.problem.offerText(), action, sticky = true))
            }
        }
    }

    private suspend fun park(
        outgoing: Outgoing,
        due: Instant,
        sendAt: Instant? = null,
    ): String = outbox.add(outgoing.draft.ready(), outgoing.draft.answered, due, sendAt)

    private suspend fun schedule(
        outgoing: Outgoing,
        id: String,
        at: LocalDateTime,
    ) {
        outbox.send(id).fold(
            onSuccess = { sent ->
                outgoing.draft.sent()
                outgoing.filed()
                val undo = sent?.let { UiAction("Undo") { scope.launch { unschedule(outgoing, it) } } }
                toasts.show(ToastMessage(dates().scheduledToast(at, outgoing.scheduledBefore), undo))
            },
            onFailure = { error ->
                val retry = UiAction("Retry") { scope.launch { schedule(outgoing, id, at) } }
                val text = if (error.isOffline()) "Will schedule when online" else "Couldn't schedule"
                toasts.show(ToastMessage(text, retry, sticky = true))
            },
        )
    }

    /**
     * Hands the message to the server to send once [hold] and a little spare time have passed, so
     * the request's trip and the server's clock cannot cut the Undo window short. Null when the
     * account cannot hold mail that long, or no answer came, so the device has to; it asks the
     * server whether the hold arrived before it sends.
     */
    private suspend fun holdOnServer(
        outgoing: Outgoing,
        id: String,
        hold: Long,
        group: Any,
    ): Result<SentMessage?>? {
        val wait = hold.milliseconds + SERVER_SPARE
        if (scheduled.maxDelaySeconds(outgoing.reopen.draft.accountId).seconds < wait) return null
        return outbox
            .send(id, holdUntil = now() + wait)
            .takeUnless { it.exceptionOrNull()?.isOffline() == true }
            ?.onSuccess { outgoing.draft.sent() }
            ?.onFailure { failed(outgoing, id, group, it) }
    }

    /**
     * Sends a message the server holds at once, unless its time is nearly up anyway, where canceling
     * and sending again would only add a request. Offline, it tries again until the server answers,
     * since the cancel may have arrived without the new submission. A failure leaves the message
     * as a draft to open.
     */
    private suspend fun sendEarly(
        outgoing: Outgoing,
        held: SentMessage,
        deadline: Instant,
        group: Any,
    ) {
        var result = if (deadline - now() <= SEND_EARLY_MIN) Result.success(Unit) else scheduled.sendNow(held)
        var wait = RETRY_FIRST
        if (result.exceptionOrNull()?.isOffline() == true) {
            toasts.show(ToastMessage(WILL_SEND, group = group, sticky = true))
        }
        while (result.exceptionOrNull()?.isOffline() == true) {
            delay(wait)
            wait = (wait * 2).coerceAtMost(RETRY_MAX)
            result = scheduled.sendNow(held)
        }
        if (result.isSuccess) {
            toasts.show(sent(group))
            return
        }
        val reopen = outgoing.reopen
        val open =
            UiAction("Open") {
                scope.launch { outgoing.open(reopen.copy(draft = reopen.draft.copy(emailId = held.emailId))) }
            }
        toasts.show(ToastMessage("Couldn't send", open, group, sticky = true))
    }

    private suspend fun deliver(
        outgoing: Outgoing,
        id: String,
        group: Any,
    ) {
        outbox.send(id).fold(
            onSuccess = {
                outgoing.draft.sent()
                toasts.show(sent(group))
            },
            onFailure = { failed(outgoing, id, group, it) },
        )
    }

    /**
     * A failed send's toast; offline, the outbox sends it once the network is back. A send nobody
     * can confirm would fail the same way again, so it offers the message to open instead of Retry.
     */
    private fun failed(
        outgoing: Outgoing,
        id: String,
        group: Any,
        error: Throwable,
    ) {
        val unconfirmed = (error as? SendException)?.failure == SendFailure.Unconfirmed
        val action =
            if (unconfirmed) {
                UiAction("Open") {
                    scope.launch {
                        outgoing.open(outgoing.reopen)
                        outbox.drop(id)
                    }
                }
            } else {
                UiAction("Retry") { scope.launch { deliver(outgoing, id, group) } }
            }
        val text =
            when {
                unconfirmed -> UNCONFIRMED
                error.isOffline() -> WILL_SEND
                else -> "Couldn't send"
            }
        toasts.show(ToastMessage(text, action, group, sticky = true))
    }

    /** Cancels a held or scheduled message and opens its copy, a draft again, to edit. */
    private suspend fun unschedule(
        outgoing: Outgoing,
        sent: SentMessage,
    ) {
        scheduled.cancel(sent).fold(
            onSuccess = {
                val reopen = outgoing.reopen
                outgoing.filed()
                outgoing.open(reopen.copy(draft = reopen.draft.copy(emailId = sent.emailId)))
            },
            onFailure = { error ->
                val message =
                    if (error is DraftRestoreException) {
                        "Send canceled. Couldn't restore the draft."
                    } else {
                        "Couldn't confirm cancellation."
                    }
                toasts.show(ToastMessage(message))
            },
        )
    }
}

private fun sent(group: Any) = ToastMessage("Sent", group = group)

private fun Throwable.isOffline() = (this as? SendException)?.failure == SendFailure.Mail(MailFailure.Unreachable)

private const val WILL_SEND = "Will send when online"
private const val UNCONFIRMED = "Couldn't confirm sending"

private fun OutboxProblem?.offerText() = if (this == OutboxProblem.Unconfirmed) UNCONFIRMED else "Couldn't send"

/** Time for the request to reach the server and for its clock to differ from the device's. */
private val SERVER_SPARE = 2.seconds

/** The first and the longest wait before sending early again while offline. */
private val RETRY_FIRST = 5.seconds
private val RETRY_MAX = 1.minutes

/** Below this much of the Undo window left, a swipe lets the server send on time. */
private val SEND_EARLY_MIN = 3.seconds
