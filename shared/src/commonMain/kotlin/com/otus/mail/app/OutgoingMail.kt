package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.shared.feedback.elements.ToastAction
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.presentation.components.scheduledLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** A draft handed over for sending: [run] sends it, at a time or now, and [open] shows [reopen] for Undo. */
internal class Outgoing(
    val run: suspend (at: Instant?) -> Result<SentMessage>,
    val reopen: ComposeRequest.Resume,
    val open: (ComposeRequest) -> Unit,
)

/**
 * Sends drafts after the compose page has closed, with toasts that say how it went. A message
 * waits [holdMillis] on the device first, read when it is sent, with Undo, which opens it again;
 * a hold of 0 sends at once. A scheduled one goes to the server at once, and Undo cancels it there.
 * A failure stays as a toast with Retry.
 */
internal class OutgoingMail(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val holdMillis: () -> Long,
) {
    fun send(outgoing: Outgoing) {
        val group = Any()
        val hold = holdMillis()
        val held =
            scope.launch {
                delay(hold)
                toasts.show(ToastMessage("Sending…", group = group))
                deliver(outgoing, group)
            }
        if (hold == 0L) return
        val undo =
            ToastAction("Undo") {
                held.cancel()
                outgoing.open(outgoing.reopen)
            }
        toasts.show(ToastMessage("Sending…", undo, group, durationMillis = hold))
    }

    fun schedule(
        outgoing: Outgoing,
        at: LocalDateTime,
    ) {
        scope.launch {
            outgoing.run(at.toInstant(TimeZone.currentSystemDefault())).fold(
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

    private suspend fun deliver(
        outgoing: Outgoing,
        group: Any,
    ) {
        outgoing.run(null).fold(
            onSuccess = { toasts.show(ToastMessage("Sent", group = group)) },
            onFailure = {
                val retry = ToastAction("Retry") { scope.launch { deliver(outgoing, group) } }
                toasts.show(ToastMessage("Couldn't send", retry, group, sticky = true))
            },
        )
    }

    /** Cancels a scheduled message and opens its copy, a draft again, to edit. */
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
