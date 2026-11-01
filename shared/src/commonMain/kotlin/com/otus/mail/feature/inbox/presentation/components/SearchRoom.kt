package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.PillRow
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the inbox panel's search room reads and does: [state]'s search and the page's [filters];
 * [onAllFilters] grows the room to every filter.
 */
internal class SearchRoom(
    val state: InboxState,
    val filters: InboxFilterState,
    val onAllFilters: () -> Unit,
)

/**
 * The one row the inbox panel grows by while a search has no query yet, looking like the panel's
 * own pills: In subject, which changes how the query matches; Has files and Past 30 days, which
 * are the page's own filters, so the filter room and the pills show them too; and All filters at
 * its end. Typing shows the results in the list and closes the room.
 */
@Composable
internal fun SearchRoomContent(room: SearchRoom) {
    val search = room.state.search
    val filters = room.filters
    val applied = filters.applied
    val files = applied.has(QuickFacet.Attachments)
    val recent = applied.date == DateFilter.Last30
    PillRow(
        Modifier.padding(top = QuickViewTokens.moduleGap),
        trailing = {
            IconButton("All filters", room.onAllFilters, look = IconButtonLook.Outlined) {
                Symbol(Res.drawable.symbol_filter_list)
            }
        },
    ) {
        Chip(
            "In subject",
            { room.state.update(search.copy(inSubject = !search.inSubject)) },
            selected = search.inSubject,
        ) {
            Symbol(Res.drawable.symbol_notes)
        }
        Chip("Has files", { filters.update(applied.with(QuickFacet.Attachments, !files)) }, selected = files) {
            Symbol(Res.drawable.symbol_attach_file)
        }
        Chip(
            "Past 30 days",
            { filters.update(applied.copy(date = if (recent) DateFilter.Any else DateFilter.Last30)) },
            selected = recent,
        ) { Symbol(Res.drawable.symbol_calendar_today) }
    }
}

/** The search room's height: its row of choices and the grab handle. */
internal fun searchRoomHeight(): Dp =
    QuickViewTokens.moduleGap +
        PanelTokens.touchTarget +
        PanelTokens.handlePadding * 2 +
        PanelTokens.handleHeight

/**
 * Grows [expansion] into the search room while [searching], and brings the panel back once the
 * search leaves it. Pushing the room shut leaves the search through [onLeave].
 */
@Composable
internal fun FollowSearch(
    expansion: PanelExpansion,
    searching: Boolean,
    onLeave: () -> Unit,
) {
    val leave by rememberUpdatedState(onLeave)
    val inRoom by rememberUpdatedState(searching)
    LaunchedEffect(searching) {
        when {
            searching -> if (expansion.stop <= 1) expansion.expandTo(1)
            expansion.expanded -> expansion.collapse()
        }
    }
    LaunchedEffect(expansion) {
        snapshotFlow { expansion.expanded }.distinctUntilChanged().collect { open ->
            if (!open && inRoom) leave()
        }
    }
}
