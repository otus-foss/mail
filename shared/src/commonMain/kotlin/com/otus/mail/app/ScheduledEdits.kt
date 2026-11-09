package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.actions.ScheduledActions
import com.otus.mail.feature.compose.presentation.Outgoing
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Edit and Send now for a message waiting in Scheduled. Each loads the copy first, so a failed load
 * leaves the message scheduled, and then cancels its send. Edit opens the draft in Write with its
 * time; leaving Write without Send keeps it as a draft, which the toast says. Send now sends the
 * copy as it is, through the usual send with its Undo, dated now.
 */
@Composable
internal fun rememberScheduledActions(
    states: MailStates,
    navigation: MailNavigation,
    page: MailPage,
): ScheduledActions {
    val scope = rememberCoroutineScope()
    val toasts = states.moveToasts.toasts
    val open: suspend (ComposeRequest) -> Unit = { navigation.go(MailPage.Compose(it, parent = page)) }
    return remember(states, navigation, page) {
        ScheduledActions(
            edit = { message ->
                scope.launch {
                    val (held, draft) = states.unscheduled(message) ?: return@launch
                    open(
                        ComposeRequest.Resume(
                            draft,
                            ComposeRequest.New(draft.accountId),
                            sendAt = held.at.toLocalDateTime(TimeZone.currentSystemDefault()),
                        ),
                    )
                    toasts.show(ToastMessage("Scheduled send canceled"))
                }
            },
            sendNow = { message ->
                scope.launch {
                    // The copy goes as it is; Undo opens it split, as Write expects.
                    val sent = states.compose.savedDraft(message, whole = true) ?: return@launch states.changeFailed()
                    val (_, draft) = states.unscheduled(message) ?: return@launch
                    states.compose.outgoing.send(
                        Outgoing(
                            OutgoingDraft(ready = { sent }, answered = null, sent = {}),
                            ComposeRequest.Resume(draft, ComposeRequest.New(draft.accountId)),
                            filed = { states.list.refreshPages(keepReport = true) },
                            open = open,
                        ),
                    )
                }
            },
        )
    }
}

/**
 * [message]'s held send and the draft its copy holds, split for Write, once its send is canceled;
 * null after a toast when the copy could not be loaded or the send not canceled.
 */
private suspend fun MailStates.unscheduled(message: MailMessage): Pair<HeldSend, Draft>? {
    val held = compose.held(message)
    val draft = held?.let { compose.savedDraft(message, whole = false) }
    if (held == null || draft == null || compose.cancel(held.message).isFailure) {
        changeFailed()
        return null
    }
    return held to draft
}

private fun MailStates.changeFailed() {
    moveToasts.toasts.show(ToastMessage("Couldn't change scheduled mail"))
}
