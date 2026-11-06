package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.shared.controls.elements.PlainAction
import com.otus.designsystem.shared.overlays.components.ConfirmDialog
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.rows.elements.RowButton
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens

/**
 * Adds custom labels and lists the ones in use with how many saved cards use each, including labels
 * only the open contact uses so far. Delete asks first, since it changes every one of those cards;
 * their values keep no label afterwards.
 */
@Composable
internal fun LabelsSheet(
    labels: ContactLabels,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<String?>(null) }
    SheetFrame("Labels", onDismiss, done = null, closeButton = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
        ) {
            PlainAction("New label", Res.drawable.symbol_add, onNew, Modifier.fillMaxWidth())
            if (labels.names.isEmpty()) Text("No custom labels yet.", color = OtusColors.text2)
            labels.names.forEach { name ->
                val uses = labels.uses(name)
                SettingRow(
                    name,
                    onClick = null,
                    description =
                        when (uses) {
                            0 -> "Only in this contact, not saved yet"
                            1 -> "On 1 card"
                            else -> "On $uses cards"
                        },
                    icon = Res.drawable.symbol_label,
                ) { RowButton("Delete", { deleting = name }, accessibilityLabel = "Delete $name") }
            }
        }
    }
    val name = deleting ?: return
    val uses = labels.uses(name)
    ConfirmDialog(
        title = "Delete “$name”?",
        message =
            if (uses == 0) {
                "No saved card uses it."
            } else {
                val cards = if (uses == 1) "1 card" else "$uses cards"
                "It’s removed from $cards. Those addresses and numbers keep no label."
            },
        confirmLabel = "Delete",
        onConfirm = {
            labels.delete(name)
            deleting = null
        },
        onDismiss = { deleting = null },
    )
}
