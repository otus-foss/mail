package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSearch

/**
 * Everything the inbox panel can ask its screen to do. [onFilter] opens a search filter's sheet,
 * [onOpenFilters] the filter panel, and [onFolder] shows a folder pill's mail, or none.
 */
class InboxPanelActions(
    val onChange: (InboxSearch) -> Unit,
    val onSearchingChange: (Boolean) -> Unit,
    val onFilter: (InboxFilter) -> Unit,
    val onOpenFilters: () -> Unit,
    val onSubmit: () -> Unit,
    val onOpenNavigation: () -> Unit,
    val onFolder: (InboxPill.FolderPill?) -> Unit,
)
