package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/**
 * Send and Discard for a draft that may edit a scheduled send. That send waits unchanged until one
 * of them runs, and both cancel it first, in [scope], which must outlive the page.
 */
internal class HeldEdits(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val outgoing: OutgoingMail,
) {
    /**
     * Sends [mail] now, or [at] a time. When the scheduled send it edits cannot be canceled, that
     * send still waits as it was, and a toast offers the draft again.
     */
    fun send(
        mail: Outgoing,
        at: LocalDateTime?,
    ) {
        scope.launch {
            val free = mail.released(scheduled) ?: return@launch offerAgain(mail)
            if (at == null) outgoing.send(free) else outgoing.schedule(free, at)
        }
    }

    /**
     * Runs [delete], which removes a discarded draft's copy. A draft that edited the scheduled send
     * [held] cancels it first, as its copy is that send's message.
     */
    fun discard(
        held: SentMessage?,
        delete: () -> Unit,
    ) {
        if (held == null) return delete()
        scope.launch { if (scheduled.cancel(held).isSuccess) delete() }
    }

    private fun offerAgain(mail: Outgoing) {
        val open = UiAction("Open") { scope.launch { mail.open(mail.reopen) } }
        toasts.show(ToastMessage(COULD_NOT_CHANGE, open))
    }
}

/** What a toast says when a scheduled mail could not be loaded or its send not canceled. */
internal const val COULD_NOT_CHANGE = "Couldn't change scheduled mail"
