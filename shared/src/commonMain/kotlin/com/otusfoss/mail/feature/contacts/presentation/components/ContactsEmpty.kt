package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_contacts
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.resources.symbol_person_search
import com.otusfoss.core.designsystem.shared.feedback.components.EmptyState
import com.otusfoss.mail.feature.contacts.presentation.ContactsState

/** The empty state for [state]'s list, or null when it has rows to show. */
internal fun emptyKind(
    state: ContactsState,
    nothingShown: Boolean,
    searching: Boolean,
): ContactsEmptyKind? {
    val snapshot = state.snapshot
    return when {
        !nothingShown -> null
        snapshot == null -> ContactsEmptyKind.Loading
        !snapshot.hasContacts -> ContactsEmptyKind.NoContacts
        searching -> ContactsEmptyKind.NoMatches
        else -> ContactsEmptyKind.Empty
    }
}

/** One title and one next step, as every Otus empty state has. Loading shows nothing yet. */
@Composable
internal fun ContactsEmpty(
    kind: ContactsEmptyKind,
    onImport: (() -> Unit)?,
) {
    when (kind) {
        ContactsEmptyKind.Loading -> return
        ContactsEmptyKind.NoContacts -> UnavailableContacts()
        ContactsEmptyKind.Empty -> NoContacts(onImport)
        ContactsEmptyKind.NoMatches -> NoMatches()
    }
}

@Composable
private fun UnavailableContacts() {
    EmptyState(
        "Contacts aren’t available",
        "Your server doesn’t offer JMAP Contacts. Mail still suggests people you’ve written to.",
        Res.drawable.symbol_contacts,
    )
}

@Composable
private fun NoContacts(onImport: (() -> Unit)?) {
    EmptyState(
        "No contacts yet",
        null,
        Res.drawable.symbol_person_add,
        actionLabel = "Import a file".takeIf { onImport != null },
        onAction = onImport ?: {},
    )
}

@Composable
private fun NoMatches() {
    EmptyState("No matching people", "Try another name or address.", Res.drawable.symbol_person_search)
}
