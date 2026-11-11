package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_add
import com.otusfoss.core.designsystem.shared.controls.elements.LocalIconTint
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.folders.elements.nestedByPath
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.designsystem.shared.rows.elements.nestIndent
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.components.SettingsSection
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.settings.presentation.components.SettingSections
import com.otusfoss.mail.core.settings.domain.MailboxStyle
import com.otusfoss.mail.core.settings.domain.PinSize
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.feature.mailsettings.presentation.components.PinnedGridEditor
import com.otusfoss.mail.feature.mailsettings.presentation.components.PinnedRowsEditor
import com.otusfoss.mail.feature.mailsettings.presentation.components.changeSidebar
import com.otusfoss.mail.feature.mailsettings.presentation.components.pinnedSettings

/**
 * Arranges the sidebar's Pinned section. Show as picks Grid or Rows, and the editor below follows
 * it, so only the controls that style has show. A grid edits like Control Center: hold a tile to
 * move it, drag its corner to resize it, or tap its minus to unpin it. Rows have no sizes: drag a
 * row's handle to move it, or tap its minus. Below, each group of the other [pins] lists what can
 * be added; tapping one pins it last. Each change is saved at once.
 */
@Composable
fun PinnedSettingsScreen(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    onBack: () -> Unit,
) {
    val layout = state.behavior.sidebar.layout
    val pinned = layout.pinned.mapNotNull { pin -> pins.firstOrNull { it.pin == pin } }
    val onMove = { from: Int, to: Int ->
        val shown = pinned.map { it.pin }
        val rest = shown.filterIndexed { index, _ -> index != from }
        state.changeSidebar { it.withPinOrder(rest.take(to) + shown[from] + rest.drop(to)) }
    }
    val onRemove = { option: SidebarPinOption -> state.changeSidebar { it.copy(pinned = it.pinned - option.pin) } }
    val onResize = { option: SidebarPinOption, size: PinSize ->
        state.changeSidebar { it.copy(sizes = it.sizes + (option.pin to size)) }
    }
    Subpage("Pinned", onBack) {
        SettingSections(pinnedSettings(state))
        SettingsSection("Shown") {
            when (layout.mailboxes) {
                MailboxStyle.Grid -> PinnedGridEditor(pinned, layout, onMove, onRemove, onResize)
                MailboxStyle.Rows -> PinnedRowsEditor(pinned, onMove, onRemove)
            }
        }
        pins.filter { it !in pinned }.groupBy { it.group }.forEach { (group, options) ->
            SettingsSection("Add ${group.lowercase()}") {
                options.nestedByPath { it.path }.forEach { (option, name, depth) ->
                    CompositionLocalProvider(LocalIconTint provides option.tint) {
                        SettingRow(
                            name,
                            { state.changeSidebar { it.copy(pinned = it.pinned + option.pin) } },
                            Modifier.nestIndent(PageTokens.nestIndent, depth),
                            icon = option.icon,
                        ) { Symbol(Res.drawable.symbol_add) }
                    }
                }
            }
        }
    }
}
