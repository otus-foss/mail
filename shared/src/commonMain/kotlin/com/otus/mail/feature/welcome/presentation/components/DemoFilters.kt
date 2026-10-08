package com.otus.mail.feature.welcome.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import com.otus.designsystem.elements.Chip

/** Demonstrates controlled filter choices without implying server-side search exists. */
@Composable
fun DemoFilters(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demo filters") },
        text = {
            Column {
                Text("These choices only update this preview.")
                listOf("Has attachments", "Unread only", "Include spam and trash").forEach { label ->
                    Chip(label, { onToggle(label) }, selected = label in selected)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
