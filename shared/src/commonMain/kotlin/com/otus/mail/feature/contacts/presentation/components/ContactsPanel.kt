package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.SearchField
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
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
    books: List<ContactBook>,
    chosen: Set<BookKey>,
    onChosenChange: (Set<BookKey>) -> Unit,
    onOpenFilters: () -> Unit,
    selected: Int,
    onOpenNavigation: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextPanel(
        modifier,
        placement = if (books.size > 1) ChipPlacement.Below else ChipPlacement.None,
        navigation = {
            if (selected > 0) {
                IconButton("Clear selection", onClearSelection, outlined = false) { Symbol(Res.drawable.symbol_close) }
            } else {
                IconButton("Open navigation", onOpenNavigation, outlined = false) {
                    Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
                }
            }
        },
        trailingAction = {
            if (books.size > 1 && selected == 0) {
                IconButton(
                    if (chosen.isEmpty()) "Open filters" else "Open filters, ${chosen.size} active",
                    onOpenFilters,
                    badgeCount = chosen.size,
                ) { Symbol(Res.drawable.symbol_filter_list) }
            }
        },
        chips = {
            if (books.size > 1) {
                item("all") {
                    Chip(
                        "All",
                        { onChosenChange(emptySet()) },
                        selected = chosen.isEmpty(),
                    ) { Symbol(Res.drawable.symbol_contacts) }
                }
                books.forEach { shown ->
                    item(shown.key) {
                        Chip(
                            shown.name,
                            { onChosenChange(chosen.toggled(shown.key)) },
                            selected = shown.key in chosen,
                        ) { Symbol(shown.icon) }
                    }
                }
            }
        },
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
                        outlined = false,
                    ) { Symbol(Res.drawable.symbol_close) }
                }
            }
        }
    }
}

/** This set with [key] added, or removed when it was in. */
internal fun Set<BookKey>.toggled(key: BookKey): Set<BookKey> = if (key in this) this - key else this + key
