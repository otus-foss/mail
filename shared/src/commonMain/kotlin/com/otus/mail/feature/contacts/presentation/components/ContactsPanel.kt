package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.input.elements.SearchField
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.panels.elements.PanelNavigationIcon
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.mail.feature.contacts.presentation.icon

/**
 * The Contacts header: a search field, or the number selected while a selection is on. Once there
 * is more than one book, a pill row below picks any number, ending in the filter button that lists every
 * book by account, as the inbox's pill row ends in its filters.
 */
@Composable
internal fun ContactsPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: BookFilter,
    selected: Int,
    onOpenNavigation: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books = filter.books
    val chosen = filter.chosen
    ContextPanel(
        modifier,
        placement = if (books.size > 1) ChipPlacement.Below else ChipPlacement.None,
        navigation = { ContactsNavigation(selected, onOpenNavigation, onClearSelection) },
        trailingAction = {
            if (books.size > 1 && selected == 0) {
                IconButton(
                    if (chosen.isEmpty()) "Open filters" else "Open filters, ${chosen.size} active",
                    filter.onOpenFilters,
                    badgeCount = chosen.size,
                ) { Symbol(Res.drawable.symbol_filter_list) }
            }
        },
        chips = {
            if (books.size > 1) {
                item("all") {
                    Chip(
                        "All",
                        { filter.onChange(emptySet()) },
                        selected = chosen.isEmpty(),
                    ) { Symbol(Res.drawable.symbol_contacts) }
                }
                books.forEach { shown ->
                    item(shown.key) {
                        Chip(
                            shown.name,
                            { filter.onChange(chosen.toggled(shown.key)) },
                            selected = shown.key in chosen,
                        ) { Symbol(shown.icon) }
                    }
                }
            }
        },
    ) { PanelSearch(query, onQueryChange, selected) }
}

/** The header's leading button: clear the selection while one is on, or else open navigation. */
@Composable
private fun ContactsNavigation(
    selected: Int,
    onOpenNavigation: () -> Unit,
    onClearSelection: () -> Unit,
) {
    if (selected > 0) {
        PanelNavigation("Clear selection", onClearSelection, icon = PanelNavigationIcon.Close)
    } else {
        PanelNavigation("Open navigation", onOpenNavigation)
    }
}

/** The search field with its clear button, or the number selected while a selection is on. */
@Composable
private fun PanelSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: Int,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (selected > 0) {
            Text("$selected selected")
        } else {
            SearchField(query, onQueryChange, "Search contacts", Modifier.weight(1f))
            if (query.isNotEmpty()) {
                IconButton(
                    "Clear search",
                    { onQueryChange("") },
                    look = IconButtonLook.Plain,
                ) { Symbol(Res.drawable.symbol_close) }
            }
        }
    }
}

/** This set with [key] added, or removed when it was in. */
internal fun <T> Set<T>.toggled(key: T): Set<T> = if (key in this) this - key else this + key
