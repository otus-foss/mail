package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SharedBy
import com.otus.mail.feature.folders.domain.SharedPlacement
import com.otus.mail.feature.folders.domain.folderTree

/**
 * A folder row in Settings › Folders. [sharedBy] says who shares the mailbox, both for a mailbox
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
 * The account's own folders, without system folders, then the mailboxes shared with it, each
 * group as its own tree. In All accounts ([allAccounts]), a mailbox whose owner is connected is
 * left to the owner's list, as [placements] says.
 */
fun AccountFolders.folderRows(
    placements: List<SharedPlacement>,
    allAccounts: Boolean,
): List<FolderListRow> {
    val here = placements.filter { it.accountId == accountId }
    val sharing = here.filter { it.underOwner }.associate { it.folder.id to it.sharedBy }
    val listedHere = here.filterNot { it.underOwner }.map { it.folder.sharedIn to it.folder.id }
    val own = folderTree { !it.isCustom }.map { FolderListRow(it, sharing[it.folder.id]) }
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
 * The drop for moving the visible row at [from] to [to]. The folder takes the place of the row
 * it lands on, beside it, or first inside it when that row is an open parent it moves down onto.
 * Null when it lands among its own subfolders or in another account.
 */
fun List<FolderListRow>.moveDrop(
    from: Int,
    to: Int,
    placeable: (MailFolder, parentId: String?) -> List<MailFolder>,
): FolderDrop? {
    val dragged = getOrNull(from)?.folder
    val target = getOrNull(to)?.folder
    if (dragged == null || target == null) return null
    val outside = dragged.sameScope(target) && !isInside(target, from)
    val opensBelow = to > from && (getOrNull(to + 1)?.node?.depth ?: 0) > this[to].node.depth
    val at = placeable(dragged, target.parentId).filter { it.id != dragged.id }.indexOfFirst { it.id == target.id }
    return when {
        !outside -> null
        opensBelow -> FolderDrop(dragged, target.id, 0)
        at < 0 -> null
        else -> FolderDrop(dragged, target.parentId, if (to > from) at + 1 else at)
    }
}

/** The drop for releasing the row at [from] onto the row at [onto]: last inside it. */
fun List<FolderListRow>.nestDrop(
    from: Int,
    onto: Int,
): FolderDrop? {
    val dragged = getOrNull(from)?.folder
    val target = getOrNull(onto)?.folder
    return if (dragged != null && target != null && dragged.sameScope(target)) {
        FolderDrop(dragged, target.id, Int.MAX_VALUE)
    } else {
        null
    }
}

/** Folders of one login and one JMAP account, which a drop can move between. */
private fun MailFolder.sameScope(other: MailFolder): Boolean =
    accountId == other.accountId && sharedIn == other.sharedIn

/** Whether [target] is the row at [from] or one of its subfolders, which follow it with a greater depth. */
private fun List<FolderListRow>.isInside(
    target: MailFolder,
    from: Int,
): Boolean {
    val depth = this[from].node.depth
    return target == this[from].folder || drop(from + 1).takeWhile { it.node.depth > depth }.any { it.folder == target }
}

/** The rows left once the subfolders of every [collapsed] row, by [FolderListRow.key], are hidden. */
fun List<FolderListRow>.visible(collapsed: Set<String>): List<FolderListRow> {
    var hiddenBelow: Int? = null
    return filter { row ->
        val depth = row.node.depth
        val hidden = hiddenBelow?.let { depth > it } ?: false
        if (!hidden) hiddenBelow = depth.takeIf { row.key in collapsed && hasChildren(row) }
        !hidden
    }
}

/** Whether subfolders follow [row] in this list. */
fun List<FolderListRow>.hasChildren(row: FolderListRow): Boolean {
    val index = indexOf(row)
    return index >= 0 && (getOrNull(index + 1)?.node?.depth ?: 0) > row.node.depth
}
