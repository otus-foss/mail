package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.shared.feedback.elements.ToastAction
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendTime
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.presentation.components.scheduledLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A draft handed over for sending: [run] sends it at a [SendTime], and [open] shows [reopen] for Undo. */
internal class Outgoing(
    val run: suspend (SendTime) -> Result<SentMessage>,
    val reopen: ComposeRequest.Resume,
    val open: (ComposeRequest) -> Unit,
)

/**
 * Sends drafts after the compose page has closed, with toasts that say how it went. A message
 * waits [holdMillis], read when it is sent, with Undo, which opens it again; a hold of 0 sends at
 * once. Where the server can hold mail that long, the message goes to the server at once and the
 * server holds it, so it still goes if the app closes; Undo cancels it there. Elsewhere the device
 * holds it. Swiping the toast away sends it at once. A scheduled one goes to the server at once
 * too. A failure stays as a toast with Retry.
 */
internal class OutgoingMail(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val now: () -> Instant = { Clock.System.now() },
    private val holdMillis: () -> Long,
) {
    fun send(outgoing: Outgoing) {
        val group = Any()
        val hold = holdMillis()
        if (hold == 0L) {
            scope.launch {
                toasts.show(ToastMessage("Sending…", group = group))
                deliver(outgoing, group)
            }
            return
        }
        val deadline = now() + hold.milliseconds
        val held = scope.async { holdOnServer(outgoing, hold, group) }
        val early = CompletableDeferred<Unit>()
        val onDevice =
            scope.launch {
                if (held.await() != null) return@launch
                withTimeoutOrNull(hold) { early.await() }
                toasts.show(ToastMessage("Sending…", group = group))
                deliver(outgoing, group)
            }
        val undo =
            ToastAction("Undo") {
                onDevice.cancel()
                scope.launch {
                    val sent = held.await()?.getOrNull()
                    if (sent == null) outgoing.open(outgoing.reopen) else unschedule(outgoing, sent)
                }
            }
        val sentToast = { scope.launch { if (held.await()?.isSuccess == true) toasts.show(sent(group)) } }
        val send =
            ToastAction("Send") {
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
            outgoing.run(SendTime.Scheduled(at.toInstant(TimeZone.currentSystemDefault()))).fold(
                onSuccess = { sent ->
                    val undo = ToastAction("Undo") { scope.launch { unschedule(outgoing, sent) } }
                    toasts.show(ToastMessage(at.scheduledLabel(), undo))
                },
                onFailure = {
                    val retry = ToastAction("Retry") { schedule(outgoing, at) }
                    toasts.show(ToastMessage("Couldn't schedule", retry, sticky = true))
                },
            )
        }
    }

    /**
     * Hands the message to the server to send once [hold] and a little spare time have passed, so
     * the request's trip and the server's clock cannot cut the Undo window short. Null when the
     * account cannot hold mail that long, so the device has to.
     */
    private suspend fun holdOnServer(
        outgoing: Outgoing,
        hold: Long,
        group: Any,
    ): Result<SentMessage>? {
        val wait = hold.milliseconds + SERVER_SPARE
        if (scheduled.maxDelaySeconds(outgoing.reopen.draft.accountId).seconds < wait) return null
        return outgoing.run(SendTime.Held(now() + wait)).onFailure { failed(outgoing, group) }
    }

    /**
     * Sends a message the server holds at once, unless its time is nearly up anyway, where canceling
     * and sending again would only add a request. A failure leaves the message as a draft to open.
     */
    private suspend fun sendEarly(
        outgoing: Outgoing,
        held: SentMessage,
        deadline: Instant,
        group: Any,
    ) {
        if (deadline - now() <= SEND_EARLY_MIN || scheduled.sendNow(held).isSuccess) {
            toasts.show(sent(group))
            return
        }
        val reopen = outgoing.reopen
        val open = ToastAction("Open") { outgoing.open(reopen.copy(draft = reopen.draft.copy(emailId = held.emailId))) }
        toasts.show(ToastMessage("Couldn't send", open, group, sticky = true))
    }

    private suspend fun deliver(
        outgoing: Outgoing,
        group: Any,
    ) {
        outgoing.run(SendTime.Now).fold(
            onSuccess = { toasts.show(sent(group)) },
            onFailure = { failed(outgoing, group) },
        )
    }

    private fun sent(group: Any) = ToastMessage("Sent", group = group)

    private fun failed(
        outgoing: Outgoing,
        group: Any,
    ) {
        val retry = ToastAction("Retry") { scope.launch { deliver(outgoing, group) } }
        toasts.show(ToastMessage("Couldn't send", retry, group, sticky = true))
    }

    /** Cancels a held or scheduled message and opens its copy, a draft again, to edit. */
    private suspend fun unschedule(
        outgoing: Outgoing,
        sent: SentMessage,
    ) {
        scheduled.cancel(sent).fold(
            onSuccess = {
                val reopen = outgoing.reopen
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

/** Time for the request to reach the server and for its clock to differ from the device's. */
private val SERVER_SPARE = 2.seconds

/** Below this much of the Undo window left, a swipe lets the server send on time. */
private val SEND_EARLY_MIN = 3.seconds
