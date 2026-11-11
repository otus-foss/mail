package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailMessage
import kotlinx.coroutines.launch

/**
 * Opens a saved draft's row in Write once its copy loaded whole, since rows carry only a preview.
 * Without it Write stays shut: a save would replace the copy with less than it had.
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
            states.compose
                .savedDraft(message, whole = false)
                ?.let {
                    navigation.go(
                        MailPage.Compose(
                            ComposeRequest.Resume(it, ComposeRequest.New(it.accountId), asSaved = true),
                            parent = page,
                        ),
                    )
                }
                ?: states.moveToasts.toasts.show(ToastMessage("Couldn't open draft"))
        }
    }
}
