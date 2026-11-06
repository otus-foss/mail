package com.otus.mail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_calendar_add_on
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.input.elements.SearchField
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import org.jetbrains.compose.resources.DrawableResource

/** Demonstrates controlled filter choices without implying server-side search exists. */
@Composable
private fun DemoFilters(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetFrame("Demo filters", onDismiss) {
        Column(
            Modifier.padding(horizontal = SheetTokens.inset).padding(bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            Text("These choices only update this preview.", color = OtusColors.text2)
            listOf("Has attachments", "Unread only", "Include spam and trash").forEach { label ->
                Chip(label, { onToggle(label) }, selected = label in selected)
            }
        }
    }
}

@Composable
private fun DemoModePicker(
    mode: PanelDemoMode,
    onSelect: (PanelDemoMode) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        PanelDemoMode.entries.forEach { option ->
            Chip(option.name, { onSelect(option) }, selected = mode == option)
        }
    }
}

@Composable
private fun DemoScaffold(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PanelTokens.panelInset),
            verticalArrangement = Arrangement.spacedBy(PanelTokens.panelInset),
        ) {
            Text("Otus Mail", style = MaterialTheme.typography.h4)
            Text("Context panel playground")
            content()
        }
    }
}

@Composable
private fun PanelDemo(
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
        navigation = { PanelNavigation("Back to inbox", { onAction(PanelDemoAction.Back) }) },
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

private enum class PanelDemoAction { Back, Search, Filters, Submit }

private enum class PanelDemoMode { Inbox, Search, Metadata }

private data class DemoChip(
    val label: String,
    val icon: DrawableResource,
)

private fun demoChips(mode: PanelDemoMode): List<DemoChip> =
    when (mode) {
        PanelDemoMode.Inbox -> {
            listOf(
                DemoChip("Unread", Res.drawable.symbol_notifications),
                DemoChip("Updates", Res.drawable.symbol_notifications),
                DemoChip("Personal", Res.drawable.symbol_person),
                DemoChip("A much longer label", Res.drawable.symbol_inbox),
            )
        }

        PanelDemoMode.Search -> {
            listOf(
                DemoChip("People", Res.drawable.symbol_person),
                DemoChip("Date", Res.drawable.symbol_calendar_add_on),
                DemoChip("Inboxes", Res.drawable.symbol_inbox),
                DemoChip("Mails", Res.drawable.symbol_alternate_email),
            )
        }

        PanelDemoMode.Metadata -> {
            listOf(
                DemoChip("2 attachments", Res.drawable.symbol_attach_file),
                DemoChip("Work", Res.drawable.symbol_inbox),
                DemoChip("Today", Res.drawable.symbol_calendar_add_on),
            )
        }
    }

@Composable
private fun PanelDemoContent(
    mode: PanelDemoMode,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    when (mode) {
        PanelDemoMode.Inbox -> {
            Text("Inbox")
        }

        PanelDemoMode.Search -> {
            SearchField(query, onQueryChange, "Search your mailbox…", onSearch = onSearch)
        }

        PanelDemoMode.Metadata -> {
            Column {
                Text("Design review")
                Text("Alex · alex@example.com")
            }
        }
    }
}

@Composable
internal fun ContextPanelPlayground() {
    var mode by rememberSaveable { mutableStateOf(PanelDemoMode.Inbox) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var overlap by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Tap search to try the overlapping layout.") }
    val keyboard = LocalSoftwareKeyboardController.current
    val changeMode: (PanelDemoMode) -> Unit = {
        mode = it
        overlap = it != PanelDemoMode.Inbox
        keyboard?.hide()
    }
    val toggle: (String) -> Unit = { label ->
        selected = if (label in selected) selected - label else selected + label
    }
    DemoScaffold {
        DemoModePicker(mode, changeMode)
        PanelDemo(
            mode = mode,
            overlap = overlap,
            query = query,
            selected = selected,
            onQueryChange = { query = it },
            onChipClick = toggle,
            onAction = { action ->
                when (action) {
                    PanelDemoAction.Back -> {
                        changeMode(PanelDemoMode.Inbox)
                    }

                    PanelDemoAction.Search -> {
                        changeMode(PanelDemoMode.Search)
                    }

                    PanelDemoAction.Filters -> {
                        showFilters = true
                    }

                    PanelDemoAction.Submit -> {
                        status = if (query.isBlank()) "Enter a search query." else "Demo query: $query"
                        keyboard?.hide()
                    }
                }
            },
        )
        Chip("Overlap chips", { overlap = !overlap }, selected = overlap)
        Text(status)
        Text("Selected: ${selected.joinToString().ifEmpty { "None" }}")
        Text("UI preview only · mail accounts and search are not connected.")
    }
    if (showFilters) DemoFilters(selected.toSet(), toggle) { showFilters = false }
}
