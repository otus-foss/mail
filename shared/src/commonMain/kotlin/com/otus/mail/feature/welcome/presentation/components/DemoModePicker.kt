package com.otus.mail.feature.welcome.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.elements.Chip

@Composable
fun DemoModePicker(
    mode: PanelDemoMode,
    onSelect: (PanelDemoMode) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        PanelDemoMode.entries.forEach { option ->
            Chip(option.name, { onSelect(option) }, selected = mode == option)
        }
    }
}
