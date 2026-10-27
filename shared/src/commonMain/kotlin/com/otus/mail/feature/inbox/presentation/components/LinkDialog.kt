package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.input.elements.InputField
import com.otus.designsystem.shared.input.elements.InputKind
import com.otus.designsystem.shared.overlays.components.DialogFrame
import com.otus.designsystem.theme.PageTokens

/** Asks for the web address of a link; Add runs [onAdd] with it, with https:// when it has no scheme. */
@Composable
internal fun LinkDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    DialogFrame("Add link", onDismiss, closeButton = false) {
        InputField("Web address", address, { address = it }, placeholder = "example.com", kind = InputKind.Url)
        Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
            ActionButton("Cancel", onDismiss, Modifier.weight(1f))
            ActionButton(
                "Add",
                {
                    onDismiss()
                    onAdd(address.trim().let { if ("://" in it || it.startsWith("mailto:")) it else "https://$it" })
                },
                Modifier.weight(1f),
                primary = true,
                enabled = address.isNotBlank(),
            )
        }
    }
}
