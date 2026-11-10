package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.sendWentAnyway
import com.otus.mail.core.messages.domain.stoppedSend
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
     * Sends [mail] now, or [at] a time, once the scheduled send it edits is stopped. When that send
     * still waits, a toast offers the edit again; when it already went, it offers the edit as a new
     * draft. Both stay until the user acts, as the edit is saved nowhere else.
     */
    fun send(
        mail: Outgoing,
        at: LocalDateTime?,
    ) {
        scope.launch {
            val canceled = mail.reopen.held?.let { scheduled.cancel(it) }
            when {
                canceled == null || canceled.stoppedSend -> go(mail.released(), at)
                canceled.sendWentAnyway -> offer(mail, ALREADY_SENT, mail.reopen.asNewDraft())
                else -> offer(mail, COULD_NOT_CHANGE, mail.reopen)
            }
        }
    }

    /**
     * Runs [delete], which removes a discarded draft's copy. A draft that edited the scheduled send
     * [held] cancels it first, as its copy is that send's message. When the send cannot be stopped,
     * the copy stays and a toast says the mail still goes.
     */
    fun discard(
        held: SentMessage?,
        delete: () -> Unit,
    ) {
        if (held == null) return delete()
        scope.launch {
            val canceled = scheduled.cancel(held)
            when {
                canceled.stoppedSend -> {
                    delete()
                }

                canceled.sendWentAnyway -> {
                    toasts.show(ToastMessage(ALREADY_SENT))
                }

                else -> {
                    toasts.show(
                        ToastMessage(STILL_SENDS, UiAction("Retry") { discard(held, delete) }, sticky = true),
                    )
                }
            }
        }
    }

    private fun go(
        mail: Outgoing,
        at: LocalDateTime?,
    ) {
        if (at == null) outgoing.send(mail) else outgoing.schedule(mail, at)
    }

    private fun offer(
        mail: Outgoing,
        text: String,
        request: ComposeRequest.Resume,
    ) {
        val open = UiAction("Open") { scope.launch { mail.open(request) } }
        toasts.show(ToastMessage(text, open, sticky = true))
    }
}

/**
 * This edit as an ordinary new draft, for a scheduled send that already went: it no longer replaces
 * that send, and its copy is the sent message, which a draft save must not replace.
 */
private fun ComposeRequest.Resume.asNewDraft() = copy(draft = draft.copy(emailId = null), held = null)

/** What a toast says when a scheduled mail could not be loaded or its send not canceled. */
internal const val COULD_NOT_CHANGE = "Couldn't change scheduled mail"

/** What a toast says when the scheduled mail an edit changes went before its send could be canceled. */
internal const val ALREADY_SENT = "Scheduled mail already sent"

/** What a toast says when a discarded edit could not cancel the scheduled send, which still goes. */
internal const val STILL_SENDS = "Couldn't discard. Scheduled mail still sends"
