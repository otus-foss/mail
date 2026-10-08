package com.otus.mail.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.elements.SelectionRow
import com.otus.mail.designsystem.theme.PageTokens

/** Single-choice popup. Choosing an option applies it and closes; dismissing changes nothing. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    detail: (T) -> String? = { null },
) {
    DialogFrame(title, onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            options.forEach { option ->
                SelectionRow(
                    label(option),
                    selected = option == selected,
                    onClick = {
                        onSelect(option)
                        onDismiss()
                    },
                    subtitle = detail(option),
                    singleChoice = true,
                )
            }
        }
    }
}
