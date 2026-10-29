package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.ContactsNavigation
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.addToGroup
import com.otus.mail.feature.contacts.presentation.book
import com.otus.mail.feature.contacts.presentation.canWriteAll

/** The Contacts bar: New contact and the list's actions, or what to do with the [chosen] people. */
@Composable
internal fun ContactsPageBar(
    state: ContactsState,
    view: ContactsView,
    chosen: List<ContactPerson>,
    navigation: ContactsNavigation,
) {
    if (view.selected.isEmpty()) {
        ContactsListBar(state.listBarActions(view, navigation.onNew))
    } else {
        ContactsSelectionBar(chosen, state.selectionBarActions(view, chosen, navigation))
    }
}

/** The list's actions; each one that cannot apply now stays null and out of the menu. */
private fun ContactsState.listBarActions(
    view: ContactsView,
    onNew: () -> Unit,
): ListBarActions =
    ListBarActions(
        onNew,
        onNewGroup = { view.naming = true }.takeIf { defaultBook != null },
        onFindDuplicates = duplicates.firstOrNull()?.let { { view.merging = mergePlan(it) } },
        duplicates = duplicates.size,
        onImport = importAction(),
        onExport = { export(cards.filterNot { it.isGroup }) }.takeIf { canTransfer && cards.isNotEmpty() },
    )

/**
 * What the bar does with the [chosen] people. Merge needs two or more cards, all of them loaded,
 * and two cards that share an address make it the primary.
 */
private fun ContactsState.selectionBarActions(
    view: ContactsView,
    chosen: List<ContactPerson>,
    navigation: ContactsNavigation,
): SelectionBarActions {
    val chosenCards = chosen.flatMap { it.cards }
    val pair = chosenCards.takeIf { it.size == 2 }?.map { it.key }?.toSet()
    val mergeable = chosenCards.takeIf { it.size >= 2 && chosen.size == view.selected.size }
    val plan = mergeable?.let { mergePlan(it) }
    return SelectionBarActions(
        onWrite = {
            navigation.onWrite(chosen.mapNotNull { it.preferredAddress })
            view.clearSelection()
        },
        onMerge = plan?.let { { view.merging = it } },
        sharedAddress = pair != null && duplicates.any { setOf(it.first.key, it.second.key) == pair },
        grouping =
            GroupChoices(
                groups.filter { group -> book(group.books.first())?.canWrite == true },
                onAdd = { group ->
                    addToGroup(group, chosenCards)
                    view.clearSelection()
                },
                onNew = { view.naming = true },
            ),
        onExport = { export(chosenCards) },
        onDelete = { view.deleting = true }.takeIf { canWriteAll(chosenCards) },
    )
}
