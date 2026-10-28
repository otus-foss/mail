package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.overlays.components.InputSheet
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.addToGroup
import com.otus.mail.feature.contacts.presentation.renameGroup

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
        GroupEdit.AddPeople -> {
            AddPeopleSheet(
                state.people.filter { person -> person.main.card.uid != null && person !in members },
                onAdd = {
                    state.addToGroup(group, it.map { person -> person.main })
                    onDone()
                },
                onDismiss = onDone,
            )
        }

        GroupEdit.Rename -> {
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
    }
}
