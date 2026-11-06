package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.InboxState

/**
 * The bar under the list: the selection's while rows are selected, none while searching, and the
 * list's otherwise. A minimized bar stays while searching, so it can sink under the keyboard
 * instead of vanishing. Without [actions] there is no bar. While the [header] panel shows every
 * filter, its Show button takes the bar's place, and while it shows a typed search, the search's.
 */
internal fun inboxBar(
    listed: ListedMail,
    state: InboxState,
    rows: InboxRows,
    actions: MessageActions?,
    onCompose: (ComposeRequest) -> Unit,
    header: InboxHeaderActions,
): (@Composable () -> Unit)? =
    when {
        header.showsFilters -> {
            filterBarContent(header)
        }

        header.showsSearchBar(state) -> {
            { SearchResultsBar(listed, state) { header.expansion.let { !it.moving && it.position >= 1f } } }
        }

        actions == null || (state.searching && !actions.saved().minimizedBar) -> {
            null
        }

        rows.selected.isNotEmpty() -> {
            selectionBarContent(rows, actions)
        }

        else -> {
            inboxBarContent(listed, state, actions, header::openFilters, onCompose)
        }
    }

private fun filterBarContent(header: InboxHeaderActions): @Composable () -> Unit =
    { InboxFilterBar(header.overlays.filters, header::showFiltered) }

private fun selectionBarContent(
    rows: InboxRows,
    actions: MessageActions,
): @Composable () -> Unit = { SelectionBar(rows.selected, actions, rows.clearSelection) }

private fun inboxBarContent(
    listed: ListedMail,
    state: InboxState,
    actions: MessageActions,
    onFilters: () -> Unit,
    onCompose: (ComposeRequest) -> Unit,
): @Composable () -> Unit = { InboxBar(listed.messages, state, listed.filters, actions, onFilters, onCompose) }

/** Row taps, long presses, and drags: opening marks a message read as the saved behavior asks. */
internal fun rowGestures(
    actions: MessageActions?,
    selected: List<MailMessage>,
    onToggle: (MailMessage) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onOpen: (MailMessage) -> Unit,
): RowGestures =
    RowGestures(actions, selected.map { it.id }.toSet(), onToggle, onSelect) { message ->
        actions?.opened(message)
        onOpen(message)
    }

/** Without [item] when it is in, else with it added at the end. */
internal fun <T> List<T>.toggled(item: T): List<T> = if (item in this) this - item else this + item

/** Without [item] when it is in, else with it added. */
internal fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
