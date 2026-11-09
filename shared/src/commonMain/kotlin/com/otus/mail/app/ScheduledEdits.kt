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
import com.otus.mail.feature.compose.presentation.COULD_NOT_CHANGE
import com.otus.mail.feature.compose.presentation.Outgoing
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Edit and Send now for a message waiting in Scheduled. Each loads the copy first, so a failed load
 * leaves the message scheduled. Edit opens the copy in Write with its time and leaves the send
 * waiting: only Send there replaces it, so leaving Write drops the changes. Send now cancels the
 * send and sends the copy as it is, through the usual send with its Undo, dated now.
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
                    val (held, draft) = states.waiting(message, whole = false) ?: return@launch
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
            sendNow = { message ->
                scope.launch {
                    val (held, draft) = states.waiting(message, whole = true) ?: return@launch
                    if (states.compose.cancel(held.message).isFailure) return@launch states.couldNotChange()
                    states.compose.outgoing.send(
                        Outgoing(
                            OutgoingDraft(ready = { draft }, answered = null, sent = {}),
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
 * [message]'s waiting send and the draft its copy holds, [whole] or split for Write; null after a
 * toast when no send waits or the copy could not be loaded.
 */
private suspend fun MailStates.waiting(
    message: MailMessage,
    whole: Boolean,
): Pair<HeldSend, Draft>? {
    val held = compose.held(message)
    val draft = held?.let { compose.savedDraft(message, whole) }
    if (held == null || draft == null) {
        couldNotChange()
        return null
    }
    return held to draft
}

private fun MailStates.couldNotChange() = moveToasts.toasts.show(ToastMessage(COULD_NOT_CHANGE))
