package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.inbox.domain.InboxFilters

/**
 * What the list's bar asks its page to do: [onFilters] grows the panel to every filter, [onFilter]
 * applies filters at once, as a quick pill does, [onSelectAll] selects the messages given,
 * [onCompose] starts a draft, and [folderPage] opens a shared folder's page where one exists.
 */
internal class InboxBarActions(
    val onFilters: () -> Unit,
    val onFilter: (InboxFilters) -> Unit,
    val onSelectAll: (List<MailMessage>) -> Unit,
    val onCompose: (ComposeRequest) -> Unit,
    val folderPage: (key: List<String>) -> (() -> Unit)?,
)
