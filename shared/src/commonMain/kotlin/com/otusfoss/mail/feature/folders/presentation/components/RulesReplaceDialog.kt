package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.overlays.components.ConfirmDialog
import com.otusfoss.mail.feature.folders.presentation.FolderRulesState

/** Asks whether a rules change may replace edits made in another app, while [rules] waits for that answer. */
@Composable
internal fun RulesReplaceDialog(rules: FolderRulesState) {
    val request = rules.replaceRequest ?: return
    ConfirmDialog(
        "Replace edits made elsewhere?",
        "These rules were changed in another app. Saving replaces those changes.",
        confirmLabel = "Replace",
        onConfirm = request.replace,
        onDismiss = rules::dismissReplace,
    )
}
