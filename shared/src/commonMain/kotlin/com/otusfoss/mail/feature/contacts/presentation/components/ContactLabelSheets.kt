package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.overlays.components.InputSheet
import com.otusfoss.mail.feature.contacts.domain.ContactDraft
import com.otusfoss.mail.feature.contacts.domain.ContactLabel

/**
 * The form's label sheets. While [managing] is set, the labels sheet is open for the field it
 * relabels; New label there swaps it for the custom label sheet, which applies the typed label to
 * that field. [labels] lists the form's own labels too, so they show before the contact is saved.
 */
@Composable
internal fun ContactLabelSheets(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    knownLabels: ContactLabels,
    labels: List<String>,
    managing: ((ContactLabel) -> Unit)?,
    onManagingDone: () -> Unit,
) {
    var naming by remember { mutableStateOf<((ContactLabel) -> Unit)?>(null) }
    val onLabel = naming
    if (onLabel != null) CustomLabelSheet(onLabel) { naming = null }
    val onManagedLabel = managing ?: return
    // Deleting also clears the label from this form's fields, which are not saved yet.
    val delete = { label: String ->
        onChange(draft.withoutLabel(label))
        knownLabels.delete(label)
    }
    LabelsSheet(
        ContactLabels(labels, knownLabels.uses, delete),
        onNew = {
            onManagingDone()
            naming = onManagedLabel
        },
        onDismiss = onManagingDone,
    )
}

/** Asks for a custom label's name and hands it to [onLabel]; a blank name cannot be used. */
@Composable
private fun CustomLabelSheet(
    onLabel: (ContactLabel) -> Unit,
    onDone: () -> Unit,
) {
    InputSheet(
        title = "Custom label",
        label = "Label",
        confirmLabel = "Use",
        onConfirm = { text ->
            onLabel(ContactLabel.Custom(text.trim()))
            onDone()
        },
        onDismiss = onDone,
        problem = { text -> if (text.isBlank()) "" else null },
    )
}
