package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanelScope
import com.otusfoss.mail.feature.inbox.domain.InboxSearch
import com.otusfoss.mail.feature.inbox.presentation.InboxContext

/**
 * Search filters while searching, the category pills and the user's pills otherwise. A shown folder
 * pill stays first in search, in place of the inbox choice: the search covers that pill's folders.
 */
@Composable
internal fun ContextPanelScope.InboxPanelChips(
    searching: Boolean,
    search: InboxSearch,
    context: InboxContext,
    dateFormatter: DateFormatter,
    selection: FilterSelection,
    actions: InboxPanelActions,
    look: PanelLook,
) {
    val shown = context.folderPill
    transition(searching) { mode ->
        if (mode) {
            if (shown != null) item(shown.pill) { FolderPillChip(shown, selected = true, actions.onFolder) }
            InboxFilter.entries.forEach { filter ->
                item(filter) {
                    SearchFilterChip(filter, search, context, dateFormatter, selection) { actions.onEditSearch() }
                }
            }
        } else {
            InboxRowPills(context.pills, look.filters, shown?.pill, actions.onFolder, look.unreadCount, look.categories)
        }
    }
}
