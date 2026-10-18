package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.barLayout
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * The message list's bar while nothing is selected. Search opens the search field, Filters runs
 * [onFilters], and Mark all read marks the messages [state] shows. Actions that need a message
 * stay disabled. A minimized bar scales out while searching, before the keyboard arrives.
 */
@Composable
internal fun InboxBar(
    messages: List<MailMessage>,
    state: InboxState,
    filters: InboxFilters,
    actions: MessageActions,
    onFilters: () -> Unit,
    onCompose: (ComposeRequest) -> Unit,
) {
    val matching = state.matching
    val results = remember(messages, matching, filters) { searchMessages(messages, matching, filters) }
    val list = ListActions(results, matching.accounts.singleOrNull(), actions, state::startSearch, onFilters, onCompose)
    val behavior = actions.behavior()
    val layout = behavior.barLayout(ActionBarPlace.Inbox, actions.cleanup?.listRole?.invoke())
    MailActionBar(layout, list::offer, onAction = {
        it.run()
    }, Minimized(hidden = state.searching).takeIf { behavior.minimizedBar })
}
