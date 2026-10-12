package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.ValuePicker
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.BarSlot

/** What the Move to row in [ActionPickerSheet] is called, and what search matches. */
internal const val MOVE_TO_FOLDER = "Move to folder…"

/**
 * The Move to row of [slot]'s picker. While the slot holds a Move to button, the row shows its
 * folder in a [ValuePicker], or says the folder is gone when none of [folders] matches it. Otherwise
 * the row offers [MOVE_TO_FOLDER]. Either way, a tap opens a sheet of [folders], and the picked
 * folder becomes a Move to button in the slot through [onPick].
 */
@Composable
internal fun MoveToChoice(
    slot: BarSlot,
    layout: ActionBarLayout,
    folders: List<BarButton.MoveTo>,
    onPick: (BarButton.MoveTo) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = layout.actionAt(slot) as? BarButton.MoveTo
    val open = { choosing = true }
    if (current == null) {
        SettingRow(MOVE_TO_FOLDER, open, icon = Res.drawable.symbol_folder, density = RowDensity.Compact)
    } else {
        val folder = folders.firstOrNull { it == current }
        SettingRow(
            "Move to",
            onClick = null,
            description = if (folder == null) "Folder not found. Pick another." else null,
            icon = Res.drawable.symbol_folder,
            density = RowDensity.Compact,
        ) {
            ValuePicker((folder ?: current).folderPath, open, icon = Res.drawable.symbol_folder)
        }
    }
    if (choosing) {
        ChoiceSheet(
            "Move to",
            // An empty slot takes only folders without a button; a filled one can trade with any.
            ChoiceOptions(folders.filter { layout.actionAt(slot) != null || layout.slotOf(it) == null }, current),
            row = { ChoiceRow(it?.folderPath.orEmpty(), layout.swapNote(slot, it)) },
            onSelect = { picked ->
                picked?.let(onPick)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The folder's names from the top level down, as pickers show its path. */
private val BarButton.MoveTo.folderPath: String
    get() = path.joinToString(FOLDER_PATH_SEPARATOR)
