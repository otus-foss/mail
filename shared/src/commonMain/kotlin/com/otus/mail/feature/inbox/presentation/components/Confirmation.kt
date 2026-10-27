package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.overlays.components.ConfirmDialog

/** The confirm dialog of a bar or menu, for actions that cannot be undone, such as deleting for good. */
internal class Confirmation {
    private var pending by mutableStateOf<Pair<OfferedAction, (OfferedAction) -> Unit>?>(null)

    /** Runs [action] with [perform] now, or once the user confirms it when it asks to be confirmed. */
    fun run(
        action: OfferedAction,
        perform: (OfferedAction) -> Unit,
    ) {
        if (action.confirm == null) perform(action) else pending = action to perform
    }

    @Composable
    fun Dialog() {
        val (action, perform) = pending ?: return
        val question = action.confirm ?: return
        ConfirmDialog(
            question.title,
            question.message,
            question.label,
            onConfirm = { perform(action) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
internal fun rememberConfirmation(): Confirmation = remember { Confirmation() }
