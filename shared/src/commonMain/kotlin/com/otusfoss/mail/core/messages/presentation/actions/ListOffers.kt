package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_filter_list_off
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.ListFilter
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MessageFlag

/**
 * What only the list's screen can do: flip its [filters], select every message through
 * [onSelectAll], and open the page of the shared folder at a lower-case path through
 * [folderPage], which gives null while no account has that folder. Each stays unavailable while
 * its is null.
 */
internal class ListExtras(
    val filters: ListFilters? = null,
    val onSelectAll: (() -> Unit)? = null,
    val folderPage: (key: List<String>) -> (() -> Unit)? = { null },
)

/** The list's filters as a bar button flips them: [isOn] says whether one is on, and [onSet] applies it at once. */
internal class ListFilters(
    val isOn: (ListFilter) -> Boolean,
    val onSet: (ListFilter, Boolean) -> Unit,
)

/** The list's own actions, which [offer] routes here. Select all needs a message to select. */
internal fun ActionSubject.offerOnList(
    list: ListSubject,
    action: MailAction,
): OfferedAction? =
    when (action) {
        MailAction.Search -> plainOffer(action, list.onSearch)
        MailAction.Filters -> plainOffer(action, list.onFilters)
        MailAction.MarkAllRead -> offerMarkAllRead(list)
        MailAction.SelectAll -> offerSelectAll(list)
        MailAction.EmptyFolder -> actions?.offerEmpty()
        else -> null
    }

private fun offerSelectAll(list: ListSubject): OfferedAction? =
    list.extras.onSelectAll
        ?.takeIf { list.results.isNotEmpty() }
        ?.let { plainOffer(MailAction.SelectAll, it) }

/** [button]'s filter as a button that says what pressing it does next: apply it, or clear it while it is on. */
internal fun ListFilters.offer(button: BarButton.Filter): OfferedAction {
    val on = isOn(button.filter)
    val name = button.label.replaceFirstChar { it.lowercase() }
    val symbol = if (on) Res.drawable.symbol_filter_list_off else button.symbol
    return OfferedAction(if (on) "Clear $name" else "Apply $name", symbol, leaves = false) { onSet(button.filter, !on) }
}

/** Opens the page of [button]'s folder; null while no account has it. */
internal fun ListSubject.offerGoTo(button: BarButton.GoTo): OfferedAction? =
    extras.folderPage(button.key)?.let { open ->
        OfferedAction(button.buttonLabel(), button.symbol, leaves = false, primaryLabel = button.name, run = open)
    }

private fun ActionSubject.offerMarkAllRead(list: ListSubject): OfferedAction? {
    val marks = marks ?: return null
    val unread = list.results.filter { it.attributes.unread && marks.can(it, MessageFlag.Read) }
    return unread.takeIf { it.isNotEmpty() }?.let {
        plainOffer(MailAction.MarkAllRead) { marks.set(unread, MessageFlag.Read, true) }
    }
}
