package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.foundation.layout.Column
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
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.input.elements.InputField
import com.otusfoss.core.designsystem.shared.input.elements.InputKind
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetBodyInset
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.parseRecipients

/**
 * Asks who a Write to button writes to: one address, or several with commas between them, as the
 * To field takes them. It starts with [current]'s addresses. Done runs [onPick] with the button
 * once every entry is an address.
 */
@Composable
internal fun WriteToSheet(
    current: BarButton.WriteTo?,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current?.addresses?.joinToString(", ").orEmpty()) }
    val addresses = parseRecipients(text).map { it.address }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    SheetFrame(
        CustomKind.WriteTo.title,
        onDismiss,
        done =
            UiAction("Done", enabled = addresses.isNotEmpty() && addresses.all(::isAddress), onClick = {
                onPick(BarButton.WriteTo(addresses))
                onDismiss()
            }),
    ) {
        Column(Modifier.sheetBodyInset()) {
            InputField(
                "Addresses",
                text,
                { text = it },
                Modifier.focusRequester(focus),
                placeholder = "ada@example.org, team@example.org",
                kind = InputKind.Email,
            )
        }
    }
}

/** Whether [text] has one @ with something on both sides. */
private fun isAddress(text: String): Boolean {
    val at = text.indexOf('@')
    return at > 0 && at < text.lastIndex && at == text.lastIndexOf('@')
}
