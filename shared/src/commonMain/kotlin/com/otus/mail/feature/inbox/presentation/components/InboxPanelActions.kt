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
    val field: SearchFieldActions,
    val onFolder: (InboxPill.FolderPill?) -> Unit,
    val opens: PanelOpens,
)

/** What the panel's search field does: [onSubmit] on the keyboard's search key, [onFocus] when the user focuses it. */
class SearchFieldActions(
    val onSubmit: () -> Unit,
    val onFocus: () -> Unit = {},
)

/** What the header's ends open: the chevron the sidebar, and the account stack the account switcher. */
class PanelOpens(
    val onOpenNavigation: () -> Unit,
    val onOpenAccounts: () -> Unit,
)
