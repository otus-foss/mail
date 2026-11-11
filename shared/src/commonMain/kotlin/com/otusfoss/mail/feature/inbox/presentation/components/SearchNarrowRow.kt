package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_calendar_today
import com.otusfoss.core.designsystem.resources.symbol_filter_list
import com.otusfoss.core.designsystem.resources.symbol_notes
import com.otusfoss.core.designsystem.resources.symbol_short_text
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButtonLook
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.panels.components.PillRow
import com.otusfoss.core.designsystem.theme.tokens.layout.QuickViewTokens
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.feature.inbox.domain.SearchField
import com.otusfoss.mail.feature.inbox.domain.toggled
import org.jetbrains.compose.resources.DrawableResource

/**
 * The search's choices, looking like the panel's own pills: Subject and Body, which change where the
 * query matches; People, Date, and Accounts, which open a sheet to pick from and show what the search
 * picked as they do over its results; Has files and Past 30 days, which are the page's own filters,
 * so the filter room and the pills show them too; and All filters at its end.
 */
@Composable
internal fun SearchNarrowRow(room: SearchRoom) {
    val focus = LocalFocusManager.current
    var picking by rememberSaveable { mutableStateOf<InboxFilter?>(null) }
    val search = room.state.search
    val page = room.sources.page
    val selection = rememberFilterSelection(room.sources.messages, search, page.mailboxSearch.accounts)
    val filterChip = @Composable { filter: InboxFilter ->
        SearchFilterChip(filter, search, page, room.dateFormatter, selection) {
            focus.clearFocus()
            picking = filter
        }
    }
    PillRow(
        Modifier.padding(top = QuickViewTokens.moduleGap),
        trailing = {
            IconButton("All filters", room.onAllFilters, look = IconButtonLook.Outlined) {
                Symbol(Res.drawable.symbol_filter_list)
            }
        },
    ) {
        fieldChoices.forEach { choice ->
            Chip(
                choice.label,
                { room.state.update(search.toggled(choice.field)) },
                selected =
                    search.field == choice.field,
            ) {
                Symbol(choice.symbol)
            }
        }
        filterChip(InboxFilter.People)
        PageFilterChips(room)
        filterChip(InboxFilter.Date)
        filterChip(InboxFilter.Accounts)
    }
    picking?.let { SearchFilterSheet(it, room) { picking = null } }
}

/** Has files and Past 30 days, which the search turns on in the page's own filters until it closes. */
@Composable
private fun PageFilterChips(room: SearchRoom) {
    val filters = room.sources.filters
    val applied = filters.applied
    val files = applied.has(QuickFacet.Attachments)
    val recent = applied.date == DateFilter.Last30
    Chip(
        "Has files",
        {
            room.changes.files(applied)
            filters.update(applied.with(QuickFacet.Attachments, !files))
        },
        selected = files,
    ) {
        Symbol(Res.drawable.symbol_attach_file)
    }
    Chip(
        "Past 30 days",
        {
            room.changes.recent(applied)
            filters.update(applied.copy(date = if (recent) DateFilter.Any else DateFilter.Last30))
        },
        selected = recent,
    ) { Symbol(Res.drawable.symbol_calendar_today) }
}

/** A narrower place a search can look in, as its chip names and draws it. */
internal class FieldChoice(
    val field: SearchField,
    val label: String,
    val symbol: DrawableResource,
)

internal val fieldChoices =
    listOf(
        FieldChoice(SearchField.Subject, "Subject", Res.drawable.symbol_short_text),
        FieldChoice(SearchField.Body, "Body", Res.drawable.symbol_notes),
    )
