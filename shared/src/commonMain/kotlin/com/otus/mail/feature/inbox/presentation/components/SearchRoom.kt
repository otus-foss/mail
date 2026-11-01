package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.filters.elements.FilterChip
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.people.components.PersonRow
import com.otus.designsystem.shared.rows.components.LocalEntriesOnRaised
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.rows.EntryTokens
import com.otus.designsystem.theme.tokens.rows.InboxTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.conversationRows
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the inbox panel's search room reads and does: [state]'s search over the mail [sources]
 * hold, narrowed by their filters. A hit opens through [gestures], and [onAllFilters] grows the room
 * to every filter.
 */
internal class SearchRoom(
    val state: InboxState,
    val sources: QuickSources,
    val gestures: RowGestures,
    val dateFormatter: DateFormatter,
    val onAllFilters: () -> Unit,
)

/**
 * The room the inbox panel grows for a search: ways to narrow it, then the people the user deals
 * with most until a query is typed, and after that the people and mail that match, as the list
 * shows them.
 */
@Composable
internal fun SearchRoomContent(room: SearchRoom) {
    Column(
        Modifier.fillMaxSize().padding(top = QuickViewTokens.moduleGap),
        verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
    ) {
        NarrowRow(room)
        if (room.state.search.query
                .isBlank()
        ) {
            Box(Modifier.padding(horizontal = SheetTokens.inset)) { QuickPeopleModule(room.sources) }
        } else {
            SearchMatches(room, Modifier.weight(1f))
        }
    }
}

/**
 * All filters, then In subject, which changes how the query matches, and Has files and Past 30
 * days, which are the page's own filters, so the filter room and the pills show them too.
 */
@Composable
private fun NarrowRow(room: SearchRoom) {
    val search = room.state.search
    val filters = room.sources.filters
    val applied = filters.applied
    val files = applied.has(QuickFacet.Attachments)
    val recent = applied.date == DateFilter.Last30
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SheetTokens.inset),
        horizontalArrangement = Arrangement.spacedBy(FilterTokens.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton("All filters", room.onAllFilters) { Symbol(Res.drawable.symbol_filter_list) }
        FilterChip("In subject", search.inSubject, { room.state.update(search.copy(inSubject = !search.inSubject)) }) {
            Symbol(Res.drawable.symbol_notes, size = FilterTokens.chipIconSize)
        }
        FilterChip("Has files", files, { filters.update(applied.with(QuickFacet.Attachments, !files)) }) {
            Symbol(Res.drawable.symbol_attach_file, size = FilterTokens.chipIconSize)
        }
        FilterChip(
            "Past 30 days",
            recent,
            { filters.update(applied.copy(date = if (recent) DateFilter.Any else DateFilter.Last30)) },
        ) { Symbol(Res.drawable.symbol_calendar_today, size = FilterTokens.chipIconSize) }
    }
}

/** The people whose name or address matches, who narrow the results to their mail, then every hit. */
@Composable
private fun SearchMatches(
    room: SearchRoom,
    modifier: Modifier,
) {
    val state = room.state
    val sources = room.sources
    val applied = sources.filters.applied
    val hits =
        remember(
            sources.messages,
            state.matching,
            applied,
        ) { searchMessages(sources.messages, state.matching, applied) }
    val people = remember(hits, state.search.query) { matchingPeople(hits, state.search.query) }
    val today = currentLocalDate()
    val look = sources.page.rowLook
    val rows =
        remember(hits, look, room.dateFormatter, today) { conversationRows(hits, look, room.dateFormatter, today) }
    // The panel is raised, so the rows take the page's color to stand out from it.
    CompositionLocalProvider(LocalEntriesOnRaised provides true) {
        LazyColumn(
            modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = InboxTokens.pageInset, end = InboxTokens.pageInset),
            verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
        ) {
            if (people.isNotEmpty()) item(key = PEOPLE_KEY) { SearchPeople(room, people) }
            if (hits.isEmpty()) item(key = EMPTY_KEY) { PageNote("No matching messages") }
            items(hits, key = { it.id }) { message ->
                rows[message.id]?.let { ConversationRow(message, it) { room.gestures.tap(message) } }
            }
        }
    }
}

@Composable
private fun SearchPeople(
    room: SearchRoom,
    people: List<MailSender>,
) {
    val state = room.state
    Column(Modifier.padding(horizontal = SheetTokens.inset - InboxTokens.pageInset)) {
        SectionCaption("People")
        people.forEach { person ->
            val fromThem = {
                state.update(state.search.copy(query = "", senders = setOf(person.address)))
                state.showResults()
            }
            PersonRow(person.shownName, person.address, fromThem) {
                PersonAvatar(person.chipAvatar, PanelTokens.stackAvatarSize)
            }
        }
    }
}

/**
 * Grows [expansion] into the search room while [searching], and again as the keyboard moves the
 * room's [height], and brings the panel back once the search leaves it. Pushing the room shut leaves
 * the search through [onLeave].
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

private const val PEOPLE_KEY = "people"
private const val EMPTY_KEY = "empty"
