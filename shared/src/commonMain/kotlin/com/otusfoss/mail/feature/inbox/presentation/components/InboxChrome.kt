package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ChooseFolder
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxState

/**
 * The bar under the list: the selection's while rows are selected, none while searching, and the
 * list's otherwise. A minimized bar stays while searching, so it can sink under the keyboard
 * instead of vanishing. Without [actions] there is no bar, and Scheduled has none: its mail is
 * edited or sent from its Read view. While the [header] panel shows every
 * filter, its Show button takes the bar's place, and while it shows a typed search, the search's.
 */
internal fun inboxBar(
    listed: ListedMail,
    state: InboxState,
    rows: InboxRows,
    actions: MessageActions?,
    page: InboxContext,
    header: InboxHeaderActions,
): (@Composable () -> Unit)? =
    when {
        header.showsFilters -> {
            filterBarContent(header)
        }

        header.showsSearchBar(state) -> {
            { SearchResultsBar(listed, state) { header.expansion.let { !it.moving && it.position >= 1f } } }
        }

        actions == null ||
            (state.searching && !actions.saved().minimizedBar) ||
            actions.cleanup?.listRole?.invoke() == MailboxRole.Scheduled -> {
            null
        }

        rows.selected.isNotEmpty() -> {
            selectionBarContent(rows, actions)
        }

        else -> {
            val bar =
                InboxBarActions(
                    header::openFilters,
                    header.overlays.filters::update,
                    rows.selectAll,
                    page.onCompose,
                    page.folderPage,
                )
            inboxBarContent(listed, state, actions, bar)
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
    page: InboxBarActions,
): @Composable () -> Unit = { InboxBar(listed, state, actions, page) }

/**
 * Row taps, long presses, drags, and swipes: opening marks a message read as the saved behavior
 * asks, and a Move swipe picks its folder through [chooseFolder].
 */
internal fun rowGestures(
    actions: MessageActions?,
    selected: List<MailMessage>,
    chooseFolder: ChooseFolder,
    onToggle: (MailMessage) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onOpen: (MailMessage) -> Unit,
): RowGestures =
    RowGestures(actions, selected.map { it.id }.toSet(), chooseFolder, onToggle, onSelect) { message ->
        actions?.opened(message)
        onOpen(message)
    }

/** Without [item] when it is in, else with it added at the end. */
internal fun <T> List<T>.toggled(item: T): List<T> = if (item in this) this - item else this + item

/** Without [item] when it is in, else with it added. */
internal fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
