package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.MessageActions

/** Runs [onBack] on system back while [enabled]. */
@Composable
internal fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

/**
 * The bar under the list: the selection's while rows are selected, none while searching, and the
 * list's otherwise. A minimized bar stays while searching, so it can sink under the keyboard
 * instead of vanishing. Without [actions] there is no bar.
 */
internal fun inboxBar(
    messages: List<MailMessage>,
    state: InboxState,
    selected: List<MailMessage>,
    actions: MessageActions?,
    clearSelection: () -> Unit,
    onCompose: (ComposeRequest) -> Unit,
    filters: InboxFilters,
    onFilters: () -> Unit,
): (@Composable () -> Unit)? =
    when {
        actions == null || (state.searching && !actions.behavior().minimizedBar) -> {
            null
        }

        selected.isNotEmpty() -> {
            { SelectionBar(selected, actions, clearSelection) }
        }

        else -> {
            { InboxBar(messages, state, filters, actions, onFilters, onCompose) }
        }
    }

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

internal fun List<String>.toggled(id: String): List<String> = if (id in this) this - id else this + id
