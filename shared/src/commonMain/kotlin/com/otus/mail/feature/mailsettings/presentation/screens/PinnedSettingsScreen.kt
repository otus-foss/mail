package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.designsystem.shared.controls.elements.LocalIconTint
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.rows.elements.nestIndent
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.presentation.nestedByPath
import com.otus.mail.core.settings.domain.MailboxStyle
import com.otus.mail.core.settings.domain.PinSize
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.core.settings.presentation.label
import com.otus.mail.feature.mailsettings.presentation.components.PinnedGridEditor
import com.otus.mail.feature.mailsettings.presentation.components.PinnedRowsEditor

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
    val layout = state.behavior.sidebar
    val change: ((SidebarLayout) -> SidebarLayout) -> Unit = { update ->
        state.update { it.copy(sidebar = update(it.sidebar)) }
    }
    var choosingStyle by rememberSaveable { mutableStateOf(false) }
    val pinned = layout.pinned.mapNotNull { pin -> pins.firstOrNull { it.pin == pin } }
    val onMove = { from: Int, to: Int ->
        val shown = pinned.map { it.pin }
        val rest = shown.filterIndexed { index, _ -> index != from }
        change { it.withPinOrder(rest.take(to) + shown[from] + rest.drop(to)) }
    }
    val onRemove = { option: SidebarPinOption -> change { it.copy(pinned = it.pinned - option.pin) } }
    val onResize = { option: SidebarPinOption, size: PinSize ->
        change { it.copy(sizes = it.sizes + (option.pin to size)) }
    }
    Subpage("Pinned", onBack) {
        SettingRow("Show as", { choosingStyle = true }, icon = Res.drawable.symbol_view_agenda) {
            ValueButton(layout.mailboxes.label)
        }
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
                            { change { it.copy(pinned = it.pinned + option.pin) } },
                            Modifier.nestIndent(PageTokens.nestIndent, depth),
                            icon = option.icon,
                        ) { Symbol(Res.drawable.symbol_add) }
                    }
                }
            }
        }
    }
    if (choosingStyle) {
        ChoiceSheet(
            title = "Show pinned as",
            choices = ChoiceOptions(MailboxStyle.entries, selected = layout.mailboxes, default = MailboxStyle.Grid),
            row = { ChoiceRow(it.label, it.detail) },
            onSelect = { style -> change { it.copy(mailboxes = style) } },
            onDismiss = { choosingStyle = false },
        )
    }
}

private val MailboxStyle.detail: String
    get() =
        when (this) {
            MailboxStyle.Grid -> "Tiles you can resize"
            MailboxStyle.Rows -> "One row each, in order"
        }
