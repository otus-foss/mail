package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.overlays.components.InputSheet
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactPerson
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.addToGroup
import com.otusfoss.mail.feature.contacts.presentation.renameGroup

/** The sheet for [edit] on [group], whose [members] are already in it; [onDone] closes it. */
@Composable
internal fun GroupEditSheet(
    edit: GroupEdit,
    state: ContactsState,
    group: ContactCard,
    members: List<ContactPerson>,
    onDone: () -> Unit,
) {
    when (edit) {
        GroupEdit.AddPeople -> AddGroupPeople(state, group, members, onDone)
        GroupEdit.Rename -> RenameGroup(state, group, onDone)
    }
}

@Composable
private fun AddGroupPeople(
    state: ContactsState,
    group: ContactCard,
    members: List<ContactPerson>,
    onDone: () -> Unit,
) {
    AddPeopleSheet(
        state.people.filter { person -> person.main.card.uid != null && person !in members },
        onAdd = {
            state.addToGroup(group, it.map { person -> person.main })
            onDone()
        },
        onDismiss = onDone,
    )
}

@Composable
private fun RenameGroup(
    state: ContactsState,
    group: ContactCard,
    onDone: () -> Unit,
) {
    InputSheet(
        title = "Rename group",
        label = "Name",
        confirmLabel = "Rename",
        onConfirm = {
            state.renameGroup(group, it)
            onDone()
        },
        onDismiss = onDone,
        initial = group.displayName,
        problem = { text -> if (text.isBlank()) "" else null },
    )
}
