package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_drive_file_move
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_palette
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.elements.ValueButton
import com.otusfoss.core.designsystem.theme.colors.FolderColor

/** The folder's name; [onRename] is null when the server does not allow renaming it. */
@Composable
internal fun FolderNameRow(
    name: String,
    onRename: (() -> Unit)?,
) {
    SettingRow("Name", onRename, icon = Res.drawable.symbol_edit) { ValueButton(name) }
}

/** Where the folder sits: under [parent], or at the top level. [onMove] is null when it cannot move. */
@Composable
internal fun FolderParentRow(
    parent: String?,
    onMove: (() -> Unit)?,
) {
    SettingRow("Move into", onMove, icon = Res.drawable.symbol_drive_file_move) { ValueButton(parent ?: TOP_LEVEL) }
}

/** The icon color stored on this device as [color], or Default for null. */
@Composable
internal fun FolderColorRow(
    color: String?,
    onClick: () -> Unit,
) {
    val shown = FolderColor.named(color)
    SettingRow("Color", onClick, icon = Res.drawable.symbol_palette) {
        ValueButton(shown.label, swatch = shown.tint)
    }
}

/** Deletes the folder; while [onDelete] is null, the row says why it cannot. */
@Composable
internal fun DeleteFolderRow(onDelete: (() -> Unit)?) {
    SettingRow(
        "Delete folder",
        onDelete,
        description = "Empty its messages and subfolders first".takeIf { onDelete == null },
        icon = Res.drawable.symbol_delete,
    )
}
