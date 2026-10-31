package com.otus.mail.feature.welcome.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens

/** Demonstrates controlled filter choices without implying server-side search exists. */
@Composable
fun DemoFilters(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetFrame("Demo filters", onDismiss) {
        Column(
            Modifier.padding(horizontal = SheetTokens.inset).padding(bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            Text("These choices only update this preview.", color = OtusColors.text2)
            listOf("Has attachments", "Unread only", "Include spam and trash").forEach { label ->
                Chip(label, { onToggle(label) }, selected = label in selected)
            }
        }
    }
}
