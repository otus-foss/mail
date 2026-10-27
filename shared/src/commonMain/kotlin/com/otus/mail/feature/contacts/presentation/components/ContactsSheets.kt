package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.overlays.components.ConfirmDialog
import com.otus.designsystem.shared.overlays.components.InputSheet
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.createGroup
import com.otus.mail.feature.contacts.presentation.delete
import com.otus.mail.feature.contacts.presentation.merge
import com.otus.mail.feature.contacts.presentation.showAsOne

/** The panels, sheets, and dialogs the Contacts page opens over itself, each shown while [view] asks. */
@Composable
internal fun ContactsSheets(
    state: ContactsState,
    view: ContactsView,
    chosen: List<ContactPerson>,
) {
    val chosenCards = chosen.flatMap { it.cards }
    ContactsFilterPanel(
        view.filtering,
        state.books,
        view.books,
        countOf = { keys -> state.peopleIn(keys).size },
        collapsedHeight = view.panelHeight,
        onApply = {
            view.books = it
            view.filtering = false
        },
        onDismiss = { view.filtering = false },
    )
    view.merging?.let { plan -> ContactsMergeSheet(state, view, plan) }
    state.importing?.let { file ->
        ImportSheet(file, state.writableBooks, state.cards, state::import) { state.importing = null }
    }
    if (view.naming) {
        InputSheet(
            title = "New group",
            label = "Name",
            confirmLabel = "Create",
            onConfirm = { name ->
                state.defaultBook?.let { state.createGroup(name, it.key, chosenCards) }
                view.naming = false
                view.clearSelection()
            },
            onDismiss = { view.naming = false },
        )
    }
    if (view.deleting) {
        DeleteDialog(chosen, onDelete = {
            state.delete(chosenCards.map { it.key })
            view.deleting = false
            view.clearSelection()
        }) { view.deleting = false }
    }
}

/** The merge sheet for [plan], which can also show the cards as one person without merging them. */
@Composable
private fun ContactsMergeSheet(
    state: ContactsState,
    view: ContactsView,
    plan: MergePlan,
) {
    MergeSheet(plan, state.writableBooks, onChange = { view.merging = it }, "Show as one", onAlternative = {
        state.showAsOne(plan.cards.map { it.key }.toSet())
        view.merging = null
        view.clearSelection()
    }, onMerge = {
        state.merge(it)
        view.merging = null
        view.clearSelection()
    }, onDismiss = { view.merging = null })
}

/** Asks before deleting the [chosen] people from the server. */
@Composable
private fun DeleteDialog(
    chosen: List<ContactPerson>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = chosen.singleOrNull()?.let { "Delete “${it.name}”?" } ?: "Delete ${chosen.size} contacts?"
    ConfirmDialog(
        title = title,
        message =
            if (chosen.size == 1) {
                "It’s removed from your server, so every app loses it."
            } else {
                "They’re removed from your server, so every app loses them."
            },
        confirmLabel = "Delete",
        onConfirm = onDelete,
        onDismiss = onDismiss,
    )
}
