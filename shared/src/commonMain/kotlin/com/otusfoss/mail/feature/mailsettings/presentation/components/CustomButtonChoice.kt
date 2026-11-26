package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.messages.presentation.label

/**
 * The row of [kind] in [slot]'s picker. While the slot holds a button of that kind, the row shows
 * the user's choice in a [ValuePicker], or says its folder is gone. Otherwise it shows the kind's
 * prompt. Either way a tap opens the kind's sheet, and what the user picks there becomes the
 * slot's button through [onPick].
 */
@Composable
internal fun CustomButtonChoice(
    kind: CustomKind,
    slot: BarSlot,
    layout: ActionBarLayout,
    choices: ButtonChoices,
    onPick: (BarButton) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = layout.actionAt(slot)?.takeIf { it.customKind == kind }
    val open = { choosing = true }
    if (current == null) {
        SettingRow(kind.prompt, open, icon = kind.symbol, density = RowDensity.Compact)
    } else {
        val folder = (current as? BarButton.ForFolder)?.let(choices::folder)
        SettingRow(
            kind.title,
            onClick = null,
            description = "Folder not found. Pick another.".takeIf { current is BarButton.ForFolder && folder == null },
            icon = kind.symbol,
            density = RowDensity.Compact,
        ) {
            CompositionLocalProvider(LocalIconTint provides FolderColor.named(folder?.color).tint) {
                ValuePicker(current.choice, open, icon = kind.symbol)
            }
        }
    }
    if (choosing) {
        val look =
            OptionLook(
                // An empty slot takes only choices without a button; a filled one can trade with any.
                hidden = { layout.actionAt(slot) == null && layout.slotOf(it) != null },
                note = { layout.swapNote(slot, it) },
            )
        CustomButtonSheet(kind, current, choices, onPick, onDismiss = { choosing = false }, look)
    }
}

/**
 * The sheet that completes a button of [kind], with [current]'s choice marked: a folder, a
 * filter, addresses, or a chain's steps. It runs [onPick] with the button and closes.
 */
@Composable
internal fun CustomButtonSheet(
    kind: CustomKind,
    current: BarButton?,
    choices: ButtonChoices,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
    look: OptionLook = OptionLook(),
) {
    when (kind) {
        CustomKind.MoveTo -> FolderSheet(kind, choices.options(look, BarButton::MoveTo), current, onPick, onDismiss)
        CustomKind.LabelAs -> FolderSheet(kind, choices.options(look, BarButton::LabelAs), current, onPick, onDismiss)
        CustomKind.GoTo -> FolderSheet(kind, choices.options(look, BarButton::GoTo), current, onPick, onDismiss)
        CustomKind.Filter -> FilterSheet(choices.filters.map(BarButton::Filter), current, look, onPick, onDismiss)
        CustomKind.WriteTo -> WriteToSheet(current as? BarButton.WriteTo, onPick, onDismiss)
        CustomKind.Chain -> ChainSheet(current as? BarButton.Chain, choices.folders, onPick, onDismiss)
    }
}

/** A folder button's row in its sheet: the folder's path with its icon in the folder's color. */
private class FolderRow(
    val button: BarButton.ForFolder,
    val row: ChoiceRow,
)

/** A button made by [make] for each folder [look] shows, as its sheet lists it. */
private fun ButtonChoices.options(
    look: OptionLook,
    make: (List<String>) -> BarButton.ForFolder,
): List<FolderRow> =
    folders
        .map { folder -> make(folder.path) to folder }
        .filterNot { (button, _) -> look.hidden(button) }
        .map { (button, folder) ->
            val tint = FolderColor.named(folder.color).tint
            val path = folder.path.pathText()
            FolderRow(button, ChoiceRow(path, look.note(button), Res.drawable.symbol_folder, iconTint = tint))
        }

@Composable
private fun FolderSheet(
    kind: CustomKind,
    folders: List<FolderRow>,
    current: BarButton?,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = folders.associate { it.button to it.row }
    PickSheet(kind.title, folders.map { it.button }, current, { rows[it] ?: ChoiceRow(it.choice) }, onPick, onDismiss)
}

@Composable
private fun FilterSheet(
    filters: List<BarButton.Filter>,
    current: BarButton?,
    look: OptionLook,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = { button: BarButton.Filter -> ChoiceRow(button.filter.label, look.note(button), button.filter.icon) }
    PickSheet(CustomKind.Filter.title, filters.filterNot(look.hidden), current, row, onPick, onDismiss)
}

/** A sheet of [options] with [current] checked; picking one runs [onPick] and closes the sheet. */
@Composable
private fun <T : BarButton> PickSheet(
    title: String,
    options: List<T>,
    current: BarButton?,
    row: (T) -> ChoiceRow,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title,
        ChoiceOptions<T?>(options, options.firstOrNull { it == current }),
        row = { it?.let(row) ?: ChoiceRow("") },
        onSelect = { picked ->
            if (picked != null) onPick(picked)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
