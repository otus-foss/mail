package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.ScheduledSends
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Discard for a draft that edited a scheduled send. [outbox] keeps the discard, so the send is
 * canceled and its copy deleted once Undo has passed, also when the app is closed by then. While
 * the app runs, a timer in [scope], which must outlive the page, does it and says how it went.
 */
internal class HeldDiscards(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val outbox: MailOutbox,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Discards the draft that edited [held], and returns what Undo runs on the request it would
     * open. Within [DISCARD_UNDO_MILLIS] the send still waits, so the request stays as it is. Later
     * the send is canceled and its copy gone, so the draft opens as an ordinary one without a copy.
     */
    fun discard(held: SentMessage): suspend (ComposeRequest.Resume) -> ComposeRequest.Resume {
        val id = scope.async { outbox.discard(held, now() + DISCARD_UNDO_MILLIS.milliseconds) }
        var undone = false
        scope.launch {
            delay(DISCARD_UNDO_MILLIS)
            if (!undone) finish(id.await(), held) { undone }
        }
        return { request ->
            undone = true
            if (outbox.drop(id.await())) request else request.copy(request.draft.copy(emailId = null), held = null)
        }
    }

    /** Cancels the send and deletes its copy, and says so when the send went or may still go, unless [undone]. */
    private suspend fun finish(
        id: String,
        held: SentMessage,
        undone: () -> Boolean,
    ) {
        val outcome = outbox.cancel(id)
        if (undone()) return
        when (outcome) {
            SendCancel.Stopped -> Unit
            SendCancel.Final -> alreadySent(held)
            SendCancel.Unknown -> toasts.show(ToastMessage(NOT_CONFIRMED))
            SendCancel.Waiting -> stillWaits { finish(id, held, undone) }
        }
    }

    private suspend fun alreadySent(held: SentMessage) {
        toasts.show(ToastMessage(ALREADY_SENT))
        scheduled.fileSent(held.accountId)
    }

    private fun stillWaits(again: suspend () -> Unit) {
        val retry = UiAction("Retry") { scope.launch { again() } }
        toasts.show(ToastMessage(MAY_STILL_SEND, retry, sticky = true))
    }
}

/** How long Undo of a discarded edit keeps its scheduled send. Its toast is given the same time. */
internal const val DISCARD_UNDO_MILLIS = 6_000L
