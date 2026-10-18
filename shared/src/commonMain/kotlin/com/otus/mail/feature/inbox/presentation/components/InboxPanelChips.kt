package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ContextPanelScope
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext

/**
 * Search filters while searching, the user's pills otherwise. A shown folder pill stays first in
 * search, in place of the inbox choice: the search covers that pill's folders.
 */
@Composable
internal fun ContextPanelScope.InboxPanelChips(
    searching: Boolean,
    search: InboxSearch,
    context: InboxContext,
    dateFormatter: DateFormatter,
    selection: FilterSelection,
    actions: InboxPanelActions,
    filters: ActiveFilters,
) {
    val shown = context.folderPill
    transition(searching) { mode ->
        if (mode) {
            shown?.let { item(it.pill) { FolderPillChip(it, selected = true, actions.onFolder) } }
            InboxFilter.entries
                .forEach { filter ->
                    item(filter) {
                        SearchFilterChip(filter, search, context, dateFormatter, selection) {
                            actions.onFilter(filter)
                        }
                    }
                }
        } else {
            InboxRowPills(context.pills, filters, shown?.pill, actions.onFolder)
        }
    }
}
