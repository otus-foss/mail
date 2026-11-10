package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendCancel
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
    private val drafts: DraftRepository,
    private val outgoing: OutgoingMail,
) {
    /**
     * Sends [mail] now, or [at] a time, once the scheduled send it edits is stopped. When that send
     * went, or the server no longer knows it, the edit is saved as a new draft. When it still
     * waits, a toast offers the edit again until the user acts, as it is saved nowhere else.
     */
    fun send(
        mail: Outgoing,
        at: LocalDateTime?,
    ) {
        scope.launch {
            val held = mail.reopen.held ?: return@launch go(mail, at)
            when (scheduled.cancel(held)) {
                SendCancel.Stopped -> {
                    go(mail.released(), at)
                }

                SendCancel.Final -> {
                    keepAsNewDraft(mail, ALREADY_SENT)
                    scheduled.fileSent(held.accountId)
                    mail.filed()
                }

                SendCancel.Unknown -> {
                    keepAsNewDraft(mail, NOT_CONFIRMED)
                }

                SendCancel.Waiting -> {
                    offer(COULD_NOT_CHANGE, mail, mail.reopen, sticky = true)
                }
            }
        }
    }

    /**
     * Runs [delete], which removes a discarded draft's copy. A draft that edited the scheduled send
     * [held] cancels it first, as its copy is that send's message, which stays when it went or may
     * still go.
     */
    fun discard(
        held: SentMessage?,
        delete: () -> Unit,
    ) {
        if (held == null) return delete()
        scope.launch {
            when (scheduled.cancel(held)) {
                SendCancel.Stopped -> {
                    delete()
                }

                SendCancel.Final -> {
                    toasts.show(ToastMessage(ALREADY_SENT))
                    scheduled.fileSent(held.accountId)
                }

                SendCancel.Unknown -> {
                    toasts.show(ToastMessage(NOT_CONFIRMED))
                }

                SendCancel.Waiting -> {
                    val retry = UiAction("Retry") { discard(held, delete) }
                    toasts.show(ToastMessage(MAY_STILL_SEND, retry, sticky = true))
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

    /** Saves [mail] as a new draft, apart from the scheduled copy, and offers it to open. */
    private suspend fun keepAsNewDraft(
        mail: Outgoing,
        text: String,
    ) {
        val saved = drafts.save(mail.draft.ready().copy(emailId = null)).getOrNull()
        val request = mail.reopen.copy(draft = mail.reopen.draft.copy(emailId = saved), held = null)
        if (saved == null) offer(text, mail, request, sticky = true) else offer("$text. $SAVED_EDIT", mail, request)
    }

    private fun offer(
        text: String,
        mail: Outgoing,
        request: ComposeRequest.Resume,
        sticky: Boolean = false,
    ) {
        val open = UiAction("Open") { scope.launch { mail.open(request) } }
        toasts.show(ToastMessage(text, open, sticky = sticky))
    }
}

/** What a toast says when a scheduled mail could not be loaded or its send not canceled. */
internal const val COULD_NOT_CHANGE = "Couldn't change scheduled mail"

/** What a toast says when the scheduled mail went before its send could be canceled. */
internal const val ALREADY_SENT = "Already sent"

/** What a toast says when the server no longer knows whether the scheduled mail went. */
internal const val NOT_CONFIRMED = "Couldn't confirm the send"

/** What a toast adds once an edit that cannot replace its scheduled mail is saved as a new draft. */
internal const val SAVED_EDIT = "Edit saved as draft"

/** What a toast says when a discarded edit could not cancel the scheduled send. */
internal const val MAY_STILL_SEND = "Couldn't discard. Scheduled mail may still send"
