package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.compose.domain.savedDraft
import kotlinx.coroutines.launch

/**
 * Opens a saved draft's row in Write once its full text loaded, since rows carry only a preview.
 * Without it Write stays shut: a save would replace the copy with the preview.
 */
@Composable
internal fun rememberDraftOpener(
    states: MailStates,
    navigation: MailNavigation,
    page: MailPage,
): (MailMessage) -> Unit {
    val scope = rememberCoroutineScope()
    return { message ->
        scope.launch {
            val content = states.messageContent(message)
            if (content == null) {
                states.moveToasts.toasts.show(ToastMessage("Couldn't open draft"))
            } else {
                val draft = message.savedDraft(content)
                navigation.go(
                    MailPage.Compose(ComposeRequest.Resume(draft, ComposeRequest.New(draft.accountId)), parent = page),
                )
            }
        }
    }
}
