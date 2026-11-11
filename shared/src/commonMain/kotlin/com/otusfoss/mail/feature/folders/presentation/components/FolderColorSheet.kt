package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.designsystem.theme.colors.FolderColor

/**
 * Picks a folder's icon color, [current] being the stored name. Default comes first, apart from
 * the colors. A choice applies at once: [onPick] gets null for Default, else the color's name.
 */
@Composable
internal fun FolderColorSheet(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Color",
        ChoiceOptions(FolderColor.entries, FolderColor.named(current), default = FolderColor.Default),
        // The swatch is decoration; screen readers hear only the color's name.
        row = { ChoiceRow(it.label, swatch = it) },
        onSelect = { onPick(it.takeUnless { color -> color == FolderColor.Default }?.name) },
        onDismiss = onDismiss,
    )
}
