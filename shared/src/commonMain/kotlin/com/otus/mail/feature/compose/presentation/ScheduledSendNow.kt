package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Send now for a message waiting in Scheduled: it cancels the send [held] and sends its copy as
 * [mail] at once, in [scope], which must outlive the page. A send that still waits offers Retry
 * until the user acts, and one that went is filed in Sent.
 */
internal class ScheduledSendNow(
    private val scope: CoroutineScope,
    private val toasts: ToastState,
    private val scheduled: ScheduledSends,
    private val outgoing: OutgoingMail,
) {
    fun send(
        held: SentMessage,
        mail: Outgoing,
    ) {
        scope.launch {
            when (scheduled.cancel(held)) {
                SendCancel.Stopped -> {
                    outgoing.send(mail)
                }

                SendCancel.Final -> {
                    toasts.show(ToastMessage(ALREADY_SENT))
                    scheduled.fileSent(held.accountId)
                    mail.filed()
                }

                SendCancel.Unknown -> {
                    toasts.show(ToastMessage(NOT_CONFIRMED))
                }

                SendCancel.Waiting -> {
                    val retry = UiAction("Retry") { send(held, mail) }
                    toasts.show(ToastMessage(COULD_NOT_CHANGE, retry, sticky = true))
                }
            }
        }
    }
}
