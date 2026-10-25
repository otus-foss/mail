package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.resources.Res
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
        secondary = moreOptions(more),
    )
}

/**
 * What the bar does with the selected people. [onMerge] is null while fewer than two cards are
 * selected; [sharedAddress] says two of them share an address, which makes Merge the primary.
 */
internal class SelectionBarActions(
    val onWrite: () -> Unit,
    val onMerge: (() -> Unit)?,
    val sharedAddress: Boolean,
    val grouping: GroupChoices,
    val onExport: () -> Unit,
    val onDelete: (() -> Unit)?,
)

/** The [groups] the selected people can join, and what adding them to one or to a new group does. */
internal class GroupChoices(
    val groups: List<ContactCard>,
    val onAdd: (ContactCard) -> Unit,
    val onNew: () -> Unit,
)

/**
 * The bar while people are selected. The primary shrinks to its icon and its context names who:
 * one person's avatar and name, or a count. Two cards that share an address make it Merge. Every
 * other action sits in the ⋯ menu.
 */
@Composable
internal fun ContactsSelectionBar(
    chosen: List<ContactPerson>,
    actions: SelectionBarActions,
) {
    val merge = actions.onMerge?.takeIf { actions.sharedAddress }
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
    val mergeItem =
        actions.onMerge
            ?.takeIf { merge == null }
            ?.let { MenuAction("Merge into one", Res.drawable.symbol_call_merge, it) }
    val groupItems =
        actions.grouping.groups.map { group ->
            MenuAction("Add to ${group.displayName}", Res.drawable.symbol_group) { actions.grouping.onAdd(group) }
        }
    val more =
        listOfNotNull(mergeItem) + groupItems +
            listOfNotNull(
                MenuAction("New group…", Res.drawable.symbol_group_add, actions.grouping.onNew),
                MenuAction("Export as vCard", Res.drawable.symbol_upload, actions.onExport),
                actions.onDelete?.let { MenuAction("Delete", Res.drawable.symbol_delete, it) },
            )
    ActionBar(
        primary,
        secondary = moreOptions(more),
        context = context,
    )
}

/** The bar's ⋯ button with every secondary action in its menu, or no button when there are none. */
internal fun moreOptions(items: List<MenuAction>): List<BarAction> =
    listOfNotNull(
        BarAction("More options", Res.drawable.symbol_more_horiz, menu = items) {}.takeIf { items.isNotEmpty() },
    )
