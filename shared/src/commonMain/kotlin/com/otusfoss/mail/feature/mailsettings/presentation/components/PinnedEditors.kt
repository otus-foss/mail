package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.mail.sidebar.components.EditableTile
import com.otusfoss.core.designsystem.mail.sidebar.components.SidebarTileEditor
import com.otusfoss.core.designsystem.mail.sidebar.elements.SidebarMailboxTile
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_remove
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButtonLook
import com.otusfoss.core.designsystem.shared.controls.elements.LocalIconTint
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.reorder.components.ReorderableColumn
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.mail.core.settings.domain.PinSize
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.pinSize
import com.otusfoss.mail.core.settings.presentation.tileSize

/**
 * The [pinned] mailboxes as the sidebar's grid draws them, in [layout]'s sizes: hold a tile to
 * move it, drag its corner to resize it, or tap its minus to unpin it.
 */
@Composable
internal fun PinnedGridEditor(
    pinned: List<SidebarPinOption>,
    layout: SidebarLayout,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (SidebarPinOption) -> Unit,
    onResize: (SidebarPinOption, PinSize) -> Unit,
) {
    SidebarTileEditor(
        pinned,
        tileOf = { EditableTile(it.pin.storageValue, it.title, layout.sizeOf(it.pin).tileSize) },
        onMove = onMove,
        onResize = { option, size -> onResize(option, size.pinSize) },
        onRemove = onRemove,
    ) { option ->
        CompositionLocalProvider(LocalIconTint provides option.tint) {
            SidebarMailboxTile(
                option.title,
                option.icon,
                selected = false,
                onClick = {},
                size = layout.sizeOf(option.pin).tileSize,
            )
        }
    }
}

/**
 * The [pinned] mailboxes as the sidebar's rows list them, which have no sizes: drag a row's handle
 * to move it, or tap its minus to unpin it.
 */
@Composable
internal fun PinnedRowsEditor(
    pinned: List<SidebarPinOption>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (SidebarPinOption) -> Unit,
) {
    ReorderableColumn(pinned, key = { it.pin.storageValue }, onMove = onMove) { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalIconTint provides option.tint) {
                SettingRow(option.title, onClick = null, Modifier.weight(1f), icon = option.icon) {
                    IconButton("Remove ${option.title}", { onRemove(option) }, look = IconButtonLook.Bare) {
                        Symbol(Res.drawable.symbol_remove)
                    }
                }
            }
            DragHandle(option.title)
        }
    }
}
