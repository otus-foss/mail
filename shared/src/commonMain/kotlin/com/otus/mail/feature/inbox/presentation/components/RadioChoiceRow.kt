package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickRowLayout

/** One of several choices: a radio first, then its [title] and what it means. */
@Composable
internal fun RadioChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PickRow(
        title,
        description,
        PickMark(selected, atStart = true),
        onClick,
        layout = PickRowLayout(subtitleLines = DESCRIPTION_LINES),
    )
}

/** Enough lines for the longest description on a narrow phone. */
private const val DESCRIPTION_LINES = 2
