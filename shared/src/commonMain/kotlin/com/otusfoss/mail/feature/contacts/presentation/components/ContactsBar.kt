package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_call_merge
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_download
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_group
import com.otusfoss.core.designsystem.resources.symbol_group_add
import com.otusfoss.core.designsystem.resources.symbol_more_horiz
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.resources.symbol_person_search
import com.otusfoss.core.designsystem.resources.symbol_upload
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBar
import com.otusfoss.core.designsystem.shared.actionbar.components.BarAction
import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.actionbar.components.BarToken
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactPerson
import com.otusfoss.mail.feature.contacts.presentation.avatar

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
            actions.onNewGroup?.let { UiAction("New group", Res.drawable.symbol_group_add, onClick = it) },
            actions.onFindDuplicates?.let {
                UiAction("Find duplicates (${actions.duplicates})", Res.drawable.symbol_person_search, onClick = it)
            },
            actions.onImport?.let { UiAction("Import from a file", Res.drawable.symbol_download, onClick = it) },
            actions.onExport?.let { UiAction("Export as vCard", Res.drawable.symbol_upload, onClick = it) },
        )
    ActionBar(
        BarAction(UiAction("New contact", Res.drawable.symbol_person_add, onClick = actions.onNew)),
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
            BarAction(UiAction("Merge", Res.drawable.symbol_call_merge, onClick = merge))
        } else {
            BarAction(
                UiAction(
                    "Write",
                    Res.drawable.symbol_edit,
                    enabled =
                        chosen.any {
                            it.emails.isNotEmpty()
                        },
                    onClick = actions.onWrite,
                ),
            )
        }
    val single = chosen.singleOrNull()
    val context =
        when {
            merge != null -> mergeContext(chosen.size)
            single != null -> BarContext(single.name, BarToken.Person(single.avatar), "to ${single.name}")
            else -> peopleContext(chosen.size)
        }
    val mergeItem =
        actions.onMerge
            ?.takeIf { merge == null }
            ?.let { UiAction("Merge into one", Res.drawable.symbol_call_merge, onClick = it) }
    val groupItems =
        actions.grouping.groups.map { group ->
            UiAction("Add to ${group.displayName}", Res.drawable.symbol_group) { actions.grouping.onAdd(group) }
        }
    val more =
        listOfNotNull(mergeItem) + groupItems +
            listOfNotNull(
                UiAction("New group…", Res.drawable.symbol_group_add, onClick = actions.grouping.onNew),
                UiAction("Export as vCard", Res.drawable.symbol_upload, onClick = actions.onExport),
                actions.onDelete?.let { UiAction("Delete", Res.drawable.symbol_delete, onClick = it) },
            )
    ActionBar(
        primary,
        secondary = moreOptions(more),
        context = context,
    )
}

private fun mergeContext(count: Int): BarContext =
    BarContext("2 cards, same address", BarToken.Count(count), "Merge 2 cards that share an address")

/** The bar's ⋯ button with every secondary action in its menu, or no button when there are none. */
internal fun moreOptions(items: List<UiAction>): List<BarAction> =
    listOfNotNull(
        BarAction(
            UiAction("More options", Res.drawable.symbol_more_horiz) {},
            menu = items,
        ).takeIf { items.isNotEmpty() },
    )
