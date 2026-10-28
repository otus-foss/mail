package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet

/**
 * Asks whether [photos] picked photos attach without their metadata and under random names, with
 * removing them checked. Closing the sheet without a choice removes them too.
 */
@Composable
internal fun PhotoCleaningSheet(
    photos: Int,
    onAnswer: (clean: Boolean) -> Unit,
) {
    ChoiceSheet(
        if (photos == 1) "Attach this photo" else "Attach $photos photos",
        ChoiceOptions(listOf(true, false), selected = true),
        row = { clean ->
            if (clean) {
                ChoiceRow("Remove details", "Location, time, camera, and file name are removed")
            } else {
                ChoiceRow("Keep everything", "Recipients can see where, when, and with what it was taken")
            }
        },
        onSelect = onAnswer,
        onDismiss = { onAnswer(true) },
        footnote = "Change the default in Settings › Privacy.",
    )
}
