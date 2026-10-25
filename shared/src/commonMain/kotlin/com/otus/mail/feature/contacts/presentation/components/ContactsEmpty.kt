package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.EmptyState
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.resources.symbol_person_search
import com.otus.mail.feature.contacts.presentation.ContactsState

/** Why the list has nothing to show. */
internal enum class ContactsEmptyKind { Loading, NoContacts, Empty, NoMatches }

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
        ContactsEmptyKind.Loading -> {
            return
        }

        ContactsEmptyKind.NoContacts -> {
            EmptyState(
                "Contacts aren’t available",
                "Your server doesn’t offer JMAP Contacts. Mail still suggests people you’ve written to.",
                Res.drawable.symbol_contacts,
            )
        }

        ContactsEmptyKind.Empty -> {
            EmptyState(
                "No contacts yet",
                "Add someone, or bring your contacts over from a vCard file.",
                Res.drawable.symbol_person_add,
                actionLabel = "Import a file".takeIf { onImport != null },
                onAction = onImport ?: {},
            )
        }

        ContactsEmptyKind.NoMatches -> {
            EmptyState("No matching people", "Try another name or address.", Res.drawable.symbol_person_search)
        }
    }
}
