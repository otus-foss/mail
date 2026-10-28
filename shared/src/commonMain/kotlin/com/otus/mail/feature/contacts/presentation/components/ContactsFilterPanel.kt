package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.filters.components.FilterPanel
import com.otus.designsystem.shared.filters.components.FilterPanelFooter
import com.otus.designsystem.shared.filters.components.FilterSection
import com.otus.designsystem.shared.filters.elements.FilterChip
import com.otus.designsystem.shared.filters.elements.FilterChipRole
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.owner

/**
 * Which address books the list shows, any number of them, on the filter panel that grows out of the Contacts header,
 * [collapsedHeight] tall. Books are grouped by whose they are, so a long list stays readable.
 * The choice applies with Show; back, the scrim, or a swipe up closes it unchanged.
 */
@Composable
internal fun ContactsFilterPanel(
    open: Boolean,
    books: List<ContactBook>,
    applied: Set<BookKey>,
    countOf: (Set<BookKey>) -> Int,
    collapsedHeight: Dp,
    onApply: (Set<BookKey>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(open) { mutableStateOf(applied) }
    FilterPanel(
        open = open,
        collapsedHeight = collapsedHeight,
        onDismiss = onDismiss,
        header = { Spacer(Modifier.height(FilterTokens.headerHeight).semantics { paneTitle = "Filters" }) },
        footer = {
            val count = countOf(draft)
            FilterPanelFooter(
                if (count == 1) "Show 1 contact" else "Show $count contacts",
                onApply = { onApply(draft) },
                onReset = { draft = emptySet() },
                applyEnabled = count > 0,
                resetEnabled = draft.isNotEmpty(),
            )
        },
        modifier =
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .padding(start = PageTokens.pageInset, end = PageTokens.pageInset, top = PageTokens.pageInset)
                .padding(bottom = FilterTokens.screenInset),
    ) {
        Column(
            Modifier.padding(horizontal = FilterTokens.bodyInset),
            verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
        ) {
            FilterSection("Show") {
                FilterChip("All contacts", draft.isEmpty(), { draft = emptySet() }, role = FilterChipRole.Radio) {
                    Symbol(Res.drawable.symbol_contacts, size = FilterTokens.chipIconSize)
                }
            }
            books.groupBy { it.owner() }.forEach { (owner, owned) ->
                FilterSection(owner) {
                    owned.forEach { book ->
                        FilterChip(book.name, book.key in draft, { draft = draft.toggled(book.key) }) {
                            Symbol(book.icon, size = FilterTokens.chipIconSize)
                        }
                    }
                }
            }
        }
    }
}
