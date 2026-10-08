package com.otus.mail.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.mail.designsystem.elements.ActionButton
import com.otus.mail.designsystem.elements.InputField
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PageTokens

/**
 * A popup that asks the user to choose: a confirmation when [message] explains the consequence,
 * or a menu of actions for one item. Buttons stack so long labels stay readable.
 */
@Composable
fun ActionDialog(
    title: String,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    DialogFrame(title, onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            message?.let { Text(it, color = OtusColors.text2) }
            actions.forEach { action ->
                ActionButton(
                    action.label,
                    {
                        onDismiss()
                        action.onClick()
                    },
                    Modifier.fillMaxWidth(),
                    primary = action.primary,
                )
            }
        }
    }
}

/**
 * A popup with one text field, such as a name. [problem] explains why the current text cannot be
 * used and disables [confirmLabel]; confirming passes the text and closes.
 */
@Composable
fun InputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    problem: (String) -> String? = { null },
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    // An untouched field shows no error yet; the button still waits for usable text.
    val error = problem(value)
    DialogFrame(title, onDismiss) {
        InputField(
            label,
            value,
            { value = it },
            Modifier.focusRequester(focus),
            supportingText = error.takeIf { value != initial },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
            ActionButton("Cancel", onDismiss, Modifier.weight(1f))
            ActionButton(
                confirmLabel,
                {
                    onConfirm(value)
                    onDismiss()
                },
                Modifier.weight(1f),
                primary = true,
                enabled = error == null,
            )
        }
    }
}
