package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.InputSheet
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.renameGroup

/** A change a group's page opens a sheet for. */
internal enum class GroupEdit { AddPeople, Rename }

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
