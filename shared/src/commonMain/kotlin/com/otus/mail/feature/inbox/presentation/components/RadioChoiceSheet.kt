package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow

/**
 * A sheet of [choices] as radio rows, each reading as its [row], starting at the selected one.
 * Done saves the picked option through [onSave] while [doneEnabled] allows it; the scrim, Back, or
 * a swipe down discards it. [below] adds content under the rows for the picked option.
 */
@Composable
internal fun <T> RadioChoiceSheet(
    title: String,
    choices: ChoiceOptions<T>,
    row: (T) -> ChoiceRow,
    onSave: (T) -> Unit,
    onDismiss: () -> Unit,
    doneEnabled: (T) -> Boolean = { true },
    below: @Composable ColumnScope.(T) -> Unit = {},
) {
    var picked by rememberSaveable { mutableStateOf(choices.selected) }
    SheetFrame(
        title,
        onDismiss,
        onDone = {
            onSave(picked)
            onDismiss()
        },
        doneEnabled = doneEnabled(picked),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.options.forEach { option ->
                val shown = row(option)
                RadioChoiceRow(shown.label, shown.detail.orEmpty(), option == picked) { picked = option }
            }
            below(picked)
        }
    }
}
