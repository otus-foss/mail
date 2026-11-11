package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.shared.controls.elements.LocalIconTint
import com.otusfoss.core.designsystem.shared.rows.components.RowDensity
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.designsystem.shared.selection.components.ValuePicker
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.theme.colors.FolderColor
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.BarSlot
import com.otusfoss.mail.core.settings.presentation.icon

/** What the Move to row in [ActionPickerSheet] is called, and what search matches. */
internal const val MOVE_TO_FOLDER = "Move to folder…"

/**
 * The Move to row of [slot]'s picker. While the slot holds a Move to button, the row shows its
 * folder in a [ValuePicker], or says the folder is gone when none of [folders] matches it. Otherwise
 * the row offers [MOVE_TO_FOLDER]. Either way, a tap opens a sheet of [folders], and the picked
 * folder becomes a Move to button in the slot through [onPick]. Each folder icon shows the color
 * [folders] names for it.
 */
@Composable
internal fun MoveToChoice(
    slot: BarSlot,
    layout: ActionBarLayout,
    folders: Map<BarButton.MoveTo, String?>,
    onPick: (BarButton.MoveTo) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = layout.actionAt(slot) as? BarButton.MoveTo
    val open = { choosing = true }
    if (current == null) {
        SettingRow(MOVE_TO_FOLDER, open, icon = Res.drawable.symbol_folder, density = RowDensity.Compact)
    } else {
        val folder = folders.keys.firstOrNull { it == current }
        SettingRow(
            "Move to",
            onClick = null,
            description = if (folder == null) "Folder not found. Pick another." else null,
            icon = Res.drawable.symbol_folder,
            density = RowDensity.Compact,
        ) {
            CompositionLocalProvider(LocalIconTint provides folders.tintOf(folder)) {
                ValuePicker((folder ?: current).folderPath, open, icon = Res.drawable.symbol_folder)
            }
        }
    }
    if (choosing) {
        ChoiceSheet(
            "Move to",
            // An empty slot takes only folders without a button; a filled one can trade with any.
            ChoiceOptions(folders.keys.filter { layout.actionAt(slot) != null || layout.slotOf(it) == null }, current),
            row = {
                ChoiceRow(
                    it?.folderPath.orEmpty(),
                    layout.swapNote(slot, it),
                    icon = Res.drawable.symbol_folder,
                    iconTint = folders.tintOf(it),
                )
            },
            onSelect = { picked ->
                if (picked != null) onPick(picked)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The folder's icon color in the current theme; null keeps the plain icon. */
private fun Map<BarButton.MoveTo, String?>.tintOf(folder: BarButton.MoveTo?): Color? =
    FolderColor.named(folder?.let(::get)).tint

/** The folder's names from the top level down, as pickers show its path. */
private val BarButton.MoveTo.folderPath: String
    get() = path.pathText()
