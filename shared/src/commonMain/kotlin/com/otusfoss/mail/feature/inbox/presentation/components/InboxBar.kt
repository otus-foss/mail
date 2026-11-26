package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.ListExtras
import com.otusfoss.mail.core.messages.presentation.actions.ListFilters
import com.otusfoss.mail.core.messages.presentation.actions.ListSubject
import com.otusfoss.mail.core.messages.presentation.actions.barFor
import com.otusfoss.mail.core.messages.presentation.actions.offer
import com.otusfoss.mail.core.messages.presentation.components.MailActionBar
import com.otusfoss.mail.core.messages.presentation.components.Minimized
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.searchMessages
import com.otusfoss.mail.feature.inbox.presentation.InboxState

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

/**
 * The message list's bar while nothing is selected. Search opens the search field, Mark all read
 * marks the messages [state] shows of [listed], Select all selects them, a Filter button turns its
 * filter on or off, and a Go to button opens its folder. Actions that need a message stay
 * disabled. A minimized bar scales out while searching, before the keyboard arrives.
 */
@Composable
internal fun InboxBar(
    listed: ListedMail,
    state: InboxState,
    actions: MessageActions,
    page: InboxBarActions,
) {
    val matching = state.matching
    val filters = listed.filters
    val results = remember(listed.messages, matching, filters) { searchMessages(listed.messages, matching, filters) }
    val extras =
        ListExtras(
            ListFilters(filters::has) { filter, on -> page.onFilter(filters.with(filter, on)) },
            onSelectAll = { page.onSelectAll(results) },
            folderPage = page.folderPage,
        )
    val account = matching.accounts.singleOrNull()
    val subject =
        ActionSubject(
            actions,
            list = ListSubject(results, account, state::startSearch, page.onFilters, page.onCompose, extras),
        )
    MailActionBar(
        actions.saved().barFor(ActionBarPlace.Inbox, actions.cleanup?.listRole?.invoke(), subject::offer),
        onAction = { it.run() },
        minimized = Minimized(hidden = state.searching),
    )
}
