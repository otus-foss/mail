package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.overlays.components.ListSheet
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.mail.core.messages.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxSearchSaver
import kotlinx.datetime.LocalDate

/**
 * The sheet a search filter's chip in the search [room] opens: the people the mail is from, the
 * days it came, or the accounts and inboxes to look in. Picks change a draft of the search that
 * Done applies; dismissing the sheet drops it.
 */
@Composable
internal fun SearchFilterSheet(
    filter: InboxFilter,
    room: SearchRoom,
    onDismiss: () -> Unit,
) {
    val state = room.state
    if (filter == InboxFilter.Date) {
        DateRangeSheet(state.search.dateRange, room.dateFormatter, { range ->
            state.update(state.search.copy(dateStart = range?.from?.toString(), dateEnd = range?.to?.toString()))
        }, onDismiss)
        return
    }
    var draft by rememberSaveable(filter, stateSaver = InboxSearchSaver) { mutableStateOf(state.search) }
    var query by rememberSaveable(filter) { mutableStateOf("") }
    val edit = { changed: InboxSearch -> draft = changed }
    ListSheet(
        if (filter == InboxFilter.People) "From" else filter.title,
        onDismiss,
        done =
            UiAction("Done", onClick = {
                state.update(draft)
                onDismiss()
            }),
        onClear = { draft = draft.cleared(filter) },
        search = { PickerSearch(query, { query = it }, "Search ${filter.title.lowercase()}…") },
    ) {
        val sources = room.sources
        if (filter == InboxFilter.People) {
            PeopleChoices(sources.messages, query.trim(), draft, edit)
        } else {
            AccountChoices(sources.page, sources.messages, query.trim(), draft, edit)
        }
    }
}

private val InboxSearch.dateRange: DateFilter.Range?
    get() = dateStart?.let { DateFilter.Range(LocalDate.parse(it), LocalDate.parse(dateEnd ?: it)) }

/** Unchecks everything in [filter]; nothing checked means all of it. */
private fun InboxSearch.cleared(filter: InboxFilter): InboxSearch =
    when (filter) {
        InboxFilter.People -> copy(senders = emptySet())
        InboxFilter.Date -> copy(dateStart = null, dateEnd = null)
        InboxFilter.Accounts -> copy(accounts = emptySet(), mailboxes = emptySet())
    }
