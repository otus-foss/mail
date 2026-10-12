package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.DialogAction

/** Asks before a change to the rules replaces a script edited in another app. */
@Composable
fun ReplaceRulesDialog(
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionDialog(
        "Replace edits made elsewhere?",
        listOf(DialogAction("Replace and save", onReplace, primary = true)),
        onDismiss = onDismiss,
        message = "These rules were edited in another app. Saving replaces that script with the rules shown here.",
    )
}
