package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
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
import com.otus.designsystem.theme.tokens.overlays.ActionBarTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.presentation.InboxState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the inbox panel's search room reads and does: [state]'s search over the mail [sources] hold,
 * narrowed by their filters. A hit opens through [gestures], and [onAllFilters] grows the room to
 * every filter.
 */
internal class SearchRoom(
    val state: InboxState,
    val sources: QuickSources,
    val gestures: RowGestures,
    val dateFormatter: DateFormatter,
    val onAllFilters: () -> Unit,
)

/**
 * The room the inbox panel grows for a search: a row of ways to narrow it, then, once a query is
 * typed, the people and mail that match, as the list shows them.
 */
@Composable
internal fun SearchRoomContent(room: SearchRoom) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap)) {
        NarrowRow(room)
        if (room.state.search.query
                .isNotBlank()
        ) {
            SearchMatches(room, Modifier.weight(1f))
        }
    }
}

/**
 * The search's choices, looking like the panel's own pills: In subject, which changes how the query
 * matches; Has files and Past 30 days, which are the page's own filters, so the filter room and the
 * pills show them too; and All filters at its end.
 */
@Composable
private fun NarrowRow(room: SearchRoom) {
    val search = room.state.search
    val filters = room.sources.filters
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

/**
 * The search room's height: its row of choices and the grab handle until something is [typed], then
 * the window below the header row and above the keyboard, less a gap.
 */
@Composable
internal fun searchRoomHeight(typed: Boolean): Dp {
    if (!typed) {
        return QuickViewTokens.moduleGap + PanelTokens.touchTarget + PanelTokens.handlePadding * 2 +
            PanelTokens.handleHeight
    }
    val window =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    return window - top - PanelTokens.headerRowHeight - keyboard - ActionBarTokens.barInset
}

/**
 * Grows [expansion] into the search room while [searching], and again as typing changes the room's
 * [height], and brings the panel back once the search leaves it. Pushing the room shut leaves the
 * search through [onLeave].
 */
@Composable
internal fun FollowSearch(
    expansion: PanelExpansion,
    searching: Boolean,
    height: Dp,
    onLeave: () -> Unit,
) {
    val leave by rememberUpdatedState(onLeave)
    val inRoom by rememberUpdatedState(searching)
    LaunchedEffect(searching, height) {
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
