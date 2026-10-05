package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.InboxSearch

/** Everything the inbox panel can ask its screen to do. */
class InboxPanelActions(
    val onChange: (InboxSearch) -> Unit,
    val onSearchingChange: (Boolean) -> Unit,
    val onFilter: (InboxFilter) -> Unit,
    val onSubmit: () -> Unit,
    val onOpenNavigation: () -> Unit,
)
