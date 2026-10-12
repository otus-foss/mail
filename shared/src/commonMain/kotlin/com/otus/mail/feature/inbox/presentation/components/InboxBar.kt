package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * The message list's bar while nothing is selected. Search opens the search field, Filters runs
 * [onFilters], and Mark all read marks the messages [state] shows. Actions that need a message
 * stay disabled.
 */
@Composable
internal fun InboxBar(
    messages: List<MailMessage>,
    state: InboxState,
    actions: MessageActions,
    onFilters: () -> Unit,
    onCompose: (ComposeRequest) -> Unit,
) {
    val matching = state.matching
    val results = remember(messages, matching) { searchMessages(messages, matching) }
    val list = ListActions(results, matching.accounts.singleOrNull(), actions, state::startSearch, onFilters, onCompose)
    val behavior = actions.behavior()
    MailActionBar(behavior.barLayout(ActionBarPlace.Inbox), list::offer, onAction = { it.run() }, behavior.minimizedBar)
}
