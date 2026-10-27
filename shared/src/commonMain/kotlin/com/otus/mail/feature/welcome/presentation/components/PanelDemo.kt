package com.otus.mail.feature.welcome.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.theme.PanelTokens

@Composable
fun PanelDemo(
    mode: PanelDemoMode,
    overlap: Boolean,
    query: String,
    selected: List<String>,
    onQueryChange: (String) -> Unit,
    onChipClick: (String) -> Unit,
    onAction: (PanelDemoAction) -> Unit,
) {
    ContextPanel(
        placement = if (overlap) ChipPlacement.Overlapping else ChipPlacement.Below,
        navigation = {
            IconButton("Back to inbox", { onAction(PanelDemoAction.Back) }, look = IconButtonLook.Plain) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
        trailingAction = {
            val search = mode == PanelDemoMode.Inbox
            IconButton(
                if (search) "Search mailbox" else "Open filters",
                { onAction(if (search) PanelDemoAction.Search else PanelDemoAction.Filters) },
            ) { Symbol(if (search) Res.drawable.symbol_search else Res.drawable.symbol_filter_list) }
        },
        chips = {
            demoChips(mode).forEach { chip ->
                item(chip.label) {
                    Chip(chip.label, { onChipClick(chip.label) }, selected = chip.label in selected) {
                        Symbol(chip.icon)
                    }
                }
            }
        },
    ) {
        PanelDemoContent(mode, query, onQueryChange) { onAction(PanelDemoAction.Submit) }
    }
}
