package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.ListSubject
import com.otusfoss.mail.core.messages.presentation.actions.barFor
import com.otusfoss.mail.core.messages.presentation.actions.offer
import com.otusfoss.mail.core.messages.presentation.components.MailActionBar
import com.otusfoss.mail.core.messages.presentation.components.Minimized
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.searchMessages
import com.otusfoss.mail.feature.inbox.presentation.InboxState

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
    val list = ListSubject(results, matching.accounts.singleOrNull(), state::startSearch, onFilters, onCompose)
    val subject = ActionSubject(actions, list = list)
    MailActionBar(
        actions.saved().barFor(ActionBarPlace.Inbox, actions.cleanup?.listRole?.invoke(), subject::offer),
        onAction = { it.run() },
        minimized = Minimized(hidden = state.searching),
    )
}
