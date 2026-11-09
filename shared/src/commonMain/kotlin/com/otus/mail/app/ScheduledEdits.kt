package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.actions.ScheduledActions
import com.otus.mail.feature.compose.domain.savedDraft
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Edit and Send now for a message waiting in Scheduled. Edit loads the message first, so a failed
 * load leaves it scheduled, then cancels the send and opens the draft in Write with its time.
 * Leaving Write without Send keeps it as a draft, which the toast says.
 */
@Composable
internal fun rememberScheduledActions(
    states: MailStates,
    navigation: MailNavigation,
    page: MailPage,
): ScheduledActions {
    val scope = rememberCoroutineScope()
    val toasts = states.moveToasts.toasts
    return remember(states, navigation, page) {
        ScheduledActions(
            edit = { message ->
                scope.launch {
                    val held = states.compose.held(message)
                    val content = held?.let { states.messageContent(message) }
                    if (held == null || content == null || states.compose.cancel(held.message).isFailure) {
                        toasts.show(ToastMessage("Couldn't edit scheduled mail"))
                        return@launch
                    }
                    val draft = message.savedDraft(content)
                    val at = held.at.toLocalDateTime(TimeZone.currentSystemDefault())
                    val request = ComposeRequest.Resume(draft, ComposeRequest.New(draft.accountId), sendAt = at)
                    navigation.go(MailPage.Compose(request, parent = page))
                    toasts.show(ToastMessage("Scheduled send canceled"))
                }
            },
            sendNow = { message: MailMessage ->
                scope.launch {
                    val held = states.compose.held(message)
                    val sent = held != null && states.compose.sendNow(held.message).isSuccess
                    toasts.show(ToastMessage(if (sent) "Sent" else "Couldn't send"))
                    if (sent) states.compose.fileScheduled()
                }
            },
        )
    }
}
