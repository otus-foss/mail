package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens

/**
 * Asks for a link's text and web address. Text starts as [selectedText], and a blank one shows
 * the address. Add runs [onAdd] with the address, with https:// when it has no scheme, and the text.
 */
@Composable
internal fun LinkSheet(
    selectedText: String,
    onAdd: (address: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(selectedText) }
    var address by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    SheetFrame(
        "Add link",
        onDismiss,
        done =
            UiAction("Add", enabled = address.isNotBlank(), onClick = {
                onAdd(address.trim().withScheme(), text.trim())
                onDismiss()
            }),
    ) {
        Column(Modifier.sheetBodyInset(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
            InputField("Text", text, { text = it }, placeholder = "Same as the address")
            InputField(
                "Web address",
                address,
                { address = it },
                Modifier.focusRequester(focus),
                kind = InputKind.Url,
            )
        }
    }
}

private fun String.withScheme(): String = if ("://" in this || startsWith("mailto:")) this else "https://$this"
