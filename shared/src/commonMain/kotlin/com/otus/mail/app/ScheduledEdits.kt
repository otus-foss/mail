package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.actions.ScheduledActions
import com.otus.mail.feature.compose.presentation.COULD_NOT_CHANGE
import com.otus.mail.feature.compose.presentation.Outgoing
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Edit and Send now for a message waiting in Scheduled. Each loads the copy first, so a failed load
 * leaves the message scheduled. Edit opens the copy in Write with its time and leaves the send
 * waiting: only Send there replaces it, so leaving Write drops the changes.
 */
@Composable
internal fun rememberScheduledActions(
    states: MailStates,
    navigation: MailNavigation,
    page: MailPage,
): ScheduledActions {
    val scope = rememberCoroutineScope()
    val open: suspend (ComposeRequest) -> Unit = { navigation.go(MailPage.Compose(it, parent = page)) }
    return remember(states, navigation, page) {
        ScheduledActions(
            edit = { message ->
                scope.launch {
                    val (held, draft) = states.waiting(message) ?: return@launch
                    open(
                        ComposeRequest.Resume(
                            draft,
                            ComposeRequest.New(draft.accountId),
                            sendAt = held.at.toLocalDateTime(TimeZone.currentSystemDefault()),
                            held = held.message,
                        ),
                    )
                }
            },
            sendNow = { message -> states.sendNow(message, open) },
        )
    }
}

/**
 * Cancels [message]'s waiting send and sends its copy as it is, through the usual send with its
 * Undo, dated now. A failed load offers Retry, as the message stays scheduled.
 */
private fun MailStates.sendNow(
    message: MailMessage,
    open: suspend (ComposeRequest) -> Unit,
) {
    work.launch {
        // The copy goes as it is; Undo opens it split, as Write expects.
        val sent = compose.savedDraft(message, whole = true)
        val (held, draft) = waiting(message, retry = { sendNow(message, open) }) ?: return@launch
        if (sent == null) return@launch changeFailed(retry = { sendNow(message, open) })
        compose.sendNow.send(
            held.message,
            Outgoing(
                OutgoingDraft(ready = { sent }, answered = null, sent = {}),
                ComposeRequest.Resume(draft, ComposeRequest.New(draft.accountId)),
                filed = { list.refreshPages(keepReport = true) },
                open = open,
            ),
        )
    }
}

/**
 * [message]'s waiting send and the draft its copy holds, split for Write; null after a toast when no
 * send waits or the copy could not be loaded.
 */
private suspend fun MailStates.waiting(
    message: MailMessage,
    retry: (() -> Unit)? = null,
): Pair<HeldSend, Draft>? {
    val held = compose.held(message)
    val draft = held?.let { compose.savedDraft(message, whole = false) }
    if (held == null || draft == null) {
        changeFailed(retry)
        return null
    }
    return held to draft
}

/** Says a scheduled mail could not be changed; with [retry], the toast offers it until the user acts. */
private fun MailStates.changeFailed(retry: (() -> Unit)? = null) {
    val action = retry?.let { UiAction("Retry") { it() } }
    moveToasts.toasts.show(ToastMessage(COULD_NOT_CHANGE, action, sticky = action != null))
}
