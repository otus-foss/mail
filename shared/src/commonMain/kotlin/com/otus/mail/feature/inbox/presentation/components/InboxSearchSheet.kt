package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.filters.elements.FilterChip
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.panels.components.PanelSheet
import com.otus.designsystem.shared.panels.components.PanelSheetHeader
import com.otus.designsystem.shared.panels.components.SearchHitRow
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.people.components.PersonRow
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SearchField
import com.otus.mail.feature.inbox.domain.lastReceived
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.domain.toggled
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.listDateLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * Search, grown out of the control panel [collapsedHeight] tall over the dimmed list: the field,
 * ways to narrow the search, the people and first messages that match, and Show all, which puts
 * every result in the list. Submitting the field shows all results too.
 */
@Composable
internal fun InboxSearchSheet(
    open: Boolean,
    search: InboxSearch,
    hits: List<MailMessage>,
    collapsedHeight: Dp,
    dates: HitDates,
    actions: SearchSheetActions,
) {
    PanelSheet(
        open,
        collapsedHeight,
        actions.onClose,
        header = {
            PanelSheetHeader(
                navigation = { PanelNavigation("Back to inbox", actions.onClose) },
                modifier = Modifier.semantics { paneTitle = "Search" },
            ) {
                InboxSearchInput(search.query, { actions.onChange(search.copy(query = it)) }, actions.onShowAll, open)
            }
        },
        Modifier.belowControlPanel().padding(bottom = FilterTokens.screenInset),
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FilterTokens.bodyInset),
            verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
        ) {
            NarrowChips(search, actions.onChange)
            if (search.query.isNotBlank()) Matches(search, hits, dates, actions)
        }
    }
}

/**
 * The search surface over the inbox while [state] searches and has not shown all results yet. A
 * hit opens through [onOpen], as a row in the list would.
 */
@Composable
internal fun InboxSearchLayer(
    state: InboxState,
    listed: ListedMail,
    collapsedHeight: Dp,
    dates: HitDates,
    onOpen: (MailMessage) -> Unit,
) {
    val focus = LocalFocusManager.current
    val hits =
        remember(listed.messages, listed.filters, state.matching) {
            searchMessages(listed.messages, state.matching, listed.filters)
        }
    InboxSearchSheet(
        state.searchSurfaceOpen,
        state.search,
        hits,
        collapsedHeight,
        dates,
        SearchSheetActions(
            onChange = state::update,
            onShowAll = {
                focus.clearFocus()
                state.showResults()
            },
            onOpen = onOpen,
            onClose = {
                focus.clearFocus()
                state.closeSearch()
            },
        ),
    )
}

/** How the search surface dates its hits, as the list does. */
internal class HitDates(
    val formatter: DateFormatter,
    val layout: MailListLayout,
)

/** What the search surface can ask its screen to do. */
internal class SearchSheetActions(
    val onChange: (InboxSearch) -> Unit,
    val onShowAll: () -> Unit,
    val onOpen: (MailMessage) -> Unit,
    val onClose: () -> Unit,
)

@Composable
private fun NarrowChips(
    search: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val today = mailToday()
    val since = remember(today) { today.minus(RECENT_DAYS, DateTimeUnit.DAY).toString() }
    val recent = search.dateStart == since && search.dateEnd == null
    Column {
        SectionCaption("Narrow it")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FilterTokens.chipGap)) {
            fieldChoices.forEach { choice ->
                FilterChip(choice.label, search.field == choice.field, { onChange(search.toggled(choice.field)) }) {
                    Symbol(choice.symbol, size = FilterTokens.chipIconSize)
                }
            }
            FilterChip("Has files", search.withFiles, { onChange(search.copy(withFiles = !search.withFiles)) }) {
                Symbol(Res.drawable.symbol_attach_file, size = FilterTokens.chipIconSize)
            }
            FilterChip(
                "Past 30 days",
                recent,
                { onChange(search.copy(dateStart = since.takeIf { !recent }, dateEnd = null)) },
            ) { Symbol(Res.drawable.symbol_calendar_today, size = FilterTokens.chipIconSize) }
        }
    }
}

/**
 * The people whose name or address matches the query, who narrow the results to their mail, then
 * the first [hits] and Show all.
 */
@Composable
private fun Matches(
    search: InboxSearch,
    hits: List<MailMessage>,
    dates: HitDates,
    actions: SearchSheetActions,
) {
    val people =
        remember(hits, search.query, search.field) {
            if (search.field == SearchField.Anywhere) matchingPeople(hits, search.query) else emptyList()
        }
    if (people.isNotEmpty()) {
        Column {
            SectionCaption("People")
            people.forEach { person ->
                val fromThem = {
                    actions.onChange(search.copy(query = "", senders = setOf(person.address)))
                    actions.onShowAll()
                }
                PersonRow(person.shownName, person.address, fromThem) {
                    PersonAvatar(person.chipAvatar, PanelTokens.stackAvatarSize)
                }
            }
        }
    }
    if (hits.isEmpty()) {
        PageNote("No matching mail")
        return
    }
    Column {
        if (people.isNotEmpty()) SectionCaption("Messages")
        val today = mailToday()
        hits.take(FIRST_HITS).forEach { message ->
            SearchHitRow(
                message.subject.ifBlank { "(No subject)" },
                dates.formatter.listDateLabel(message.lastReceived, today, dates.layout),
                { actions.onOpen(message) },
            )
        }
    }
    ActionButton(
        if (hits.size == 1) "Show 1 result" else "Show all ${hits.size} results",
        actions.onShowAll,
        Modifier.fillMaxWidth(),
    )
}

/** The senders of [hits] whose name or address holds every term of [query], at most [limit] of them. */
internal fun matchingPeople(
    hits: List<MailMessage>,
    query: String,
    limit: Int = FIRST_PEOPLE,
): List<MailSender> =
    hits
        .map { it.sender }
        .distinctBy { it.address.lowercase() }
        .filter { sender ->
            query
                .trim()
                .split(
                    Regex("\\s+"),
                ).all { sender.name.contains(it, true) || sender.address.contains(it, true) }
        }.take(limit)

private const val RECENT_DAYS = 30

private const val FIRST_HITS = 3
private const val FIRST_PEOPLE = 2
