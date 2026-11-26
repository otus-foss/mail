package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SentMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Undo of a send: takes the message back and says how that went, in [scope], which outlives the page. */
internal class SendUndo(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
) {
    /**
     * Takes [outgoing] back with [cancel] and opens it to edit once it is stopped. [sent] is the
     * copy the server held, which the cancel made a draft again; without it the draft opens as it
     * was saved. A message that went says so, in the toast of [group] when it has one.
     */
    suspend fun takeBack(
        outgoing: Outgoing,
        sent: SentMessage?,
        group: Any? = null,
        cancel: suspend () -> SendCancel,
    ) {
        when (cancel()) {
            SendCancel.Stopped -> reopen(outgoing, sent)
            SendCancel.Final -> toasts.show(ToastMessage(ALREADY_SENT, group = group))
            SendCancel.Unknown -> unknown(outgoing, sent)
            SendCancel.Waiting -> waiting(outgoing, sent, group, cancel)
        }
    }

    /**
     * A hold the server confirmed still waits there, so a toast offers to try the cancel again.
     * Without [sent] nobody knows whether the server has the message, so the draft opens at once
     * and the server is asked again until it answers.
     */
    private suspend fun waiting(
        outgoing: Outgoing,
        sent: SentMessage?,
        group: Any?,
        cancel: suspend () -> SendCancel,
    ) {
        if (sent != null) {
            val retry = UiAction("Retry") { scope.launch { takeBack(outgoing, sent, group, cancel) } }
            return toasts.show(ToastMessage("Couldn't cancel", retry, sticky = true))
        }
        reopen(outgoing, sent = null)
        when (retried(SendCancel.Waiting, { it == SendCancel.Waiting }, cancel)) {
            SendCancel.Final -> toasts.show(ToastMessage(ALREADY_SENT, group = group))
            SendCancel.Unknown -> toasts.show(ToastMessage(NOT_CONFIRMED))
            SendCancel.Stopped, SendCancel.Waiting -> Unit
        }
    }

    private suspend fun unknown(
        outgoing: Outgoing,
        sent: SentMessage?,
    ) {
        if (sent == null) reopen(outgoing, sent = null)
        toasts.show(ToastMessage(NOT_CONFIRMED))
    }

    private suspend fun reopen(
        outgoing: Outgoing,
        sent: SentMessage?,
    ) {
        if (sent == null) return outgoing.open(outgoing.reopenSaved())
        outgoing.filed()
        // A copy that is not a draft again yet still opens: its first save replaces it.
        outgoing.open(outgoing.reopen.copy(draft = outgoing.reopen.draft.copy(emailId = sent.emailId)))
    }
}

/**
 * [attempt] again for as long as its result, [first] to begin with, [waits] for the server. The
 * first wait is 5 seconds, and each next one twice as long, up to a minute.
 */
internal suspend fun <T> retried(
    first: T,
    waits: (T) -> Boolean,
    attempt: suspend () -> T,
): T {
    var result = first
    var wait = RETRY_FIRST
    while (waits(result)) {
        delay(wait)
        wait = (wait * 2).coerceAtMost(RETRY_MAX)
        result = attempt()
    }
    return result
}

private val RETRY_FIRST = 5.seconds
private val RETRY_MAX = 1.minutes
