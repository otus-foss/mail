package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.shared.reorder.components.TreeDropTarget
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.feature.folders.domain.SharedBy
import com.otus.mail.feature.folders.domain.SharedPlacement

/**
 * A folder row in Settings › Folders & rules. [sharedBy] says who shares the mailbox, both for a mailbox
 * shared with this login and for one of its own folders it shares with another connected login.
 */
data class FolderListRow(
    val node: FolderNode,
    val sharedBy: SharedBy? = null,
) {
    val folder: MailFolder
        get() = node.folder

    /** Unique on the page: a shared mailbox's id can repeat one of the login's own. */
    val key: String
        get() = listOfNotNull(folder.accountId, folder.sharedIn, folder.id).joinToString("/")
}

/**
 * The account's own folders, without system folders or Read later, then the mailboxes shared with
 * it, each group as its own tree. In All accounts ([allAccounts]), a mailbox whose owner is connected is
 * left to the owner's list, as [placements] says.
 */
fun AccountFolders.folderRows(
    placements: List<SharedPlacement>,
    allAccounts: Boolean,
): List<FolderListRow> {
    val here = placements.filter { it.accountId == accountId }
    val sharing = here.filter { it.underOwner }.associate { it.folder.id to it.sharedBy }
    val listedHere = here.filterNot { it.underOwner }.map { it.folder.sharedIn to it.folder.id }
    // Read later is one list across accounts that its switch manages, so no account lists its folder here.
    val own = folderTree { !isOwnFolder(it) }.map { FolderListRow(it, sharing[it.folder.id]) }
    val shared =
        shared.flatMap { group ->
            val folders = group.folders.filter { !allAccounts || (it.sharedIn to it.id) in listedHere }
            folderTree(folders).map { FolderListRow(it, SharedBy(group.owner, it.folder.rights.mayAddItems)) }
        }
    return own + shared
}

/** Where a drag lands: [folder] under [parentId] at [index] among the folders placed there. */
data class FolderDrop(
    val folder: MailFolder,
    val parentId: String?,
    val index: Int,
)

/**
 * The drop for the visible row at [from] landing on [target]. In a gap, the folder goes right
 * before the row below it, among that row's siblings, or after the last row among its siblings.
 * Onto a row, it becomes that row's first subfolder. Null when [target] is in another account;
 * `FolderPlacement` refuses the rest, such as a drop inside the folder itself.
 */
fun List<FolderListRow>.treeDrop(
    from: Int,
    target: TreeDropTarget,
    placeable: (MailFolder, parentId: String?) -> List<MailFolder>,
): FolderDrop? {
    val dragged = getOrNull(from)?.folder
    val anchor = getOrNull(target.index.coerceAtMost(lastIndex))?.folder
    if (dragged == null || anchor == null || !dragged.sameScope(anchor)) return null
    return when (target) {
        is TreeDropTarget.Into -> FolderDrop(dragged, anchor.id, index = 0)
        is TreeDropTarget.Before -> dropBefore(dragged, anchor, target.index, placeable)
    }
}

private fun List<FolderListRow>.dropBefore(
    dragged: MailFolder,
    anchor: MailFolder,
    index: Int,
    placeable: (MailFolder, parentId: String?) -> List<MailFolder>,
): FolderDrop {
    val siblings = placeable(dragged, anchor.parentId).filter { it.id != dragged.id }
    return FolderDrop(dragged, anchor.parentId, take(index).count { it.folder in siblings })
}

/** Folders of one login and one JMAP account, which a drop can move between. */
private fun MailFolder.sameScope(other: MailFolder): Boolean =
    accountId == other.accountId && sharedIn == other.sharedIn

/** The rows left once the subfolders of every [collapsed] row, by [FolderListRow.key], are hidden. */
fun List<FolderListRow>.visible(collapsed: Set<String>): List<FolderListRow> {
    var hiddenBelow: Int? = null
    return filter { row ->
        val depth = row.node.depth
        val below = hiddenBelow
        val hidden = below != null && depth > below
        if (!hidden) hiddenBelow = depth.takeIf { row.key in collapsed && hasChildren(row) }
        !hidden
    }
}

/** Whether subfolders follow [row] in this list. */
fun List<FolderListRow>.hasChildren(row: FolderListRow): Boolean {
    val index = indexOf(row)
    return index >= 0 && (getOrNull(index + 1)?.node?.depth ?: 0) > row.node.depth
}
