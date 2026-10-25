package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_download
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_group_add
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.resources.symbol_person_search
import com.otus.designsystem.resources.symbol_upload
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.peopleCount

/** What the list's bar offers while nothing is selected. Null actions are left out of the ⋯ menu. */
internal class ListBarActions(
    val onNew: () -> Unit,
    val onNewGroup: (() -> Unit)?,
    val onFindDuplicates: (() -> Unit)?,
    val duplicates: Int,
    val onImport: (() -> Unit)?,
    val onExport: (() -> Unit)?,
)

/** ⋯ and a wide New contact, as the inbox has ⋯ and New mail. */
@Composable
internal fun ContactsListBar(actions: ListBarActions) {
    val more =
        listOfNotNull(
            actions.onNewGroup?.let { MenuAction("New group", Res.drawable.symbol_group_add, it) },
            actions.onFindDuplicates?.let {
                MenuAction("Find duplicates (${actions.duplicates})", Res.drawable.symbol_person_search, it)
            },
            actions.onImport?.let { MenuAction("Import from a file", Res.drawable.symbol_download, it) },
            actions.onExport?.let { MenuAction("Export as vCard", Res.drawable.symbol_upload, it) },
        )
    ActionBar(
        BarAction("New contact", Res.drawable.symbol_person_add, onClick = actions.onNew),
        secondary =
            listOfNotNull(
                BarAction("More options", Res.drawable.symbol_more_horiz, menu = more) {
                }.takeIf { more.isNotEmpty() },
            ),
    )
}

/** What the bar does with the selected people. */
internal class SelectionBarActions(
    val onWrite: () -> Unit,
    val onMerge: (() -> Unit)?,
    val groups: List<ContactCard>,
    val onAddToGroup: (ContactCard) -> Unit,
    val onNewGroup: () -> Unit,
    val onExport: () -> Unit,
    val onDelete: (() -> Unit)?,
)

/**
 * The bar while people are selected. The primary shrinks to its icon and its context names who:
 * one person's avatar and name, or a count. Two cards that share an address make it Merge.
 */
@Composable
internal fun ContactsSelectionBar(
    chosen: List<ContactPerson>,
    actions: SelectionBarActions,
) {
    val merge = actions.onMerge
    val primary =
        if (merge != null) {
            BarAction("Merge", Res.drawable.symbol_call_merge, onClick = merge)
        } else {
            BarAction(
                "Write",
                Res.drawable.symbol_edit,
                enabled = chosen.any { it.emails.isNotEmpty() },
                onClick = actions.onWrite,
            )
        }
    val single = chosen.singleOrNull()
    val context =
        when {
            merge != null -> {
                BarContext(
                    "2 cards, same address",
                    BarToken.Count(chosen.size),
                    "Merge 2 cards that share an address",
                )
            }

            single != null -> {
                BarContext(single.name, BarToken.Person(single.avatar), "to ${single.name}")
            }

            else -> {
                BarContext(peopleCount(chosen.size), BarToken.Count(chosen.size), "to ${peopleCount(chosen.size)}")
            }
        }
    val groupMenu =
        actions.groups.map { MenuAction(it.displayName, Res.drawable.symbol_group) { actions.onAddToGroup(it) } } +
            MenuAction("New group…", Res.drawable.symbol_add, actions.onNewGroup)
    ActionBar(
        primary,
        secondary =
            listOfNotNull(
                BarAction("Add to a group", Res.drawable.symbol_group_add, menu = groupMenu) {},
                BarAction("Export as vCard", Res.drawable.symbol_upload, onClick = actions.onExport),
                actions.onDelete?.let { BarAction("Delete", Res.drawable.symbol_delete, onClick = it) },
            ),
        context = context,
    )
}
