package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.shared.controls.elements.LocalIconTint
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.rows.elements.nestIndent
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.sidebar.components.EditableTile
import com.otus.designsystem.shared.sidebar.components.SidebarTileEditor
import com.otus.designsystem.shared.sidebar.elements.SidebarMailboxTile
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.nestedByPath
import com.otus.mail.feature.inbox.presentation.pinSize
import com.otus.mail.feature.inbox.presentation.tileSize

/**
 * Arranges the sidebar's Pinned section the way Control Center does. The editor shows the pinned
 * mailboxes from [pins] as the sidebar's grid draws them: hold a tile to move it, drag its corner
 * to resize it, or tap its minus to unpin it. Below, each group of the other [pins] lists what can
 * be added; tapping one pins it last. Each change is saved at once.
 */
@Composable
fun PinnedSettingsScreen(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    onBack: () -> Unit,
) {
    val layout = state.behavior.sidebar
    val change: ((SidebarLayout) -> SidebarLayout) -> Unit = { update ->
        state.update { it.copy(sidebar = update(it.sidebar)) }
    }
    val pinned = layout.pinned.mapNotNull { pin -> pins.firstOrNull { it.pin == pin } }
    Subpage("Pinned", onBack) {
        SettingsSection("Shown") {
            SidebarTileEditor(
                pinned,
                tileOf = { EditableTile(it.pin.storageValue, it.title, layout.sizeOf(it.pin).tileSize) },
                onMove = { from, to ->
                    val shown = pinned.map { it.pin }
                    val rest = shown.filterIndexed { index, _ -> index != from }
                    change { it.withPinOrder(rest.take(to) + shown[from] + rest.drop(to)) }
                },
                onResize = { option, size -> change { it.copy(sizes = it.sizes + (option.pin to size.pinSize)) } },
                onRemove = { option -> change { it.copy(pinned = it.pinned - option.pin) } },
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
        pins.filter { it !in pinned }.groupBy { it.group }.forEach { (group, options) ->
            SettingsSection("Add ${group.lowercase()}") {
                options.nestedByPath { it.path }.forEach { (option, name, depth) ->
                    CompositionLocalProvider(LocalIconTint provides option.tint) {
                        SettingRow(
                            name,
                            { change { it.copy(pinned = it.pinned + option.pin) } },
                            Modifier.nestIndent(PageTokens.nestIndent, depth),
                            icon = option.icon,
                        ) { Symbol(Res.drawable.symbol_add) }
                    }
                }
            }
        }
    }
}
